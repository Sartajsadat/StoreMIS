package af.storemis.inventory.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.inventory.dto.AdjustmentRequest;
import af.storemis.inventory.dto.AdjustmentResponse;
import af.storemis.inventory.entity.AdjustmentType;
import af.storemis.inventory.entity.ProductStock;
import af.storemis.inventory.entity.StockAdjustment;
import af.storemis.inventory.entity.StockSourceType;
import af.storemis.inventory.repository.StockAdjustmentRepository;
import af.storemis.inventory.repository.StockMovementRepository;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stock adjustments: opening stock, increases, decreases and damage. Each one is a numbered, immutable document. */
@Service
public class AdjustmentService {

    private final StockAdjustmentRepository adjustments;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final InventoryPostingService posting;
    private final InventoryQueryService queries;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final AuditService audit;
    private final BusinessClock clock;

    public AdjustmentService(StockAdjustmentRepository adjustments, StockMovementRepository movements, ProductRepository products,
            InventoryPostingService posting, InventoryQueryService queries, DocumentNumberService numbers, UserDirectory userDirectory,
            AuditService audit, BusinessClock clock) {
        this.adjustments = adjustments;
        this.movements = movements;
        this.products = products;
        this.posting = posting;
        this.queries = queries;
        this.numbers = numbers;
        this.userDirectory = userDirectory;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AdjustmentResponse create(AdjustmentRequest r) {
        if (r.clientRequestId() != null) {
            Optional<StockAdjustment> existing = adjustments.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        if (r.type() == AdjustmentType.OPENING && !SecurityUtils.requirePrincipal().isAdmin()) {
            throw new AccessDeniedException("Opening stock is admin-only");
        }
        Product product = products.findById(r.productId()).orElseThrow(() -> new NotFoundException("Product", r.productId()));
        if (!product.isActive()) throw new BusinessException(ErrorCode.PRODUCT_INACTIVE, Map.of("product", product.getName()));
        return toResponse(post(product, r.type(), r.quantity(), r.unitCost(), r.reason().trim(), r.clientRequestId()));
    }

    /** Also used when a product is created with opening stock. Must run inside a transaction. */
    @Transactional
    public StockAdjustment post(Product product, AdjustmentType type, BigDecimal quantity, BigDecimal unitCost, String reason, java.util.UUID clientRequestId) {
        Map<Long, ProductStock> locked = posting.lock(List.of(product.getId()));
        ProductStock stock = locked.get(product.getId());
        BigDecimal cost;
        if (type == AdjustmentType.OPENING) {
            if (movements.existsByProductId(product.getId())) {
                throw new BusinessException(ErrorCode.OPENING_STOCK_EXISTS, Map.of("product", product.getName()));
            }
            if (unitCost == null) throw new BusinessException(ErrorCode.UNIT_COST_REQUIRED);
            cost = unitCost;
        } else if (type == AdjustmentType.INCREASE) {
            cost = unitCost != null ? unitCost : stock.getAvgUnitCost();
        } else {
            cost = stock.getAvgUnitCost();
        }

        LocalDate today = clock.today();
        String number = numbers.next(DocumentType.STOCK_ADJUSTMENT);
        StockAdjustment adj = adjustments.save(new StockAdjustment(number, clientRequestId, product.getId(), type, StockMath.qty(quantity),
                StockMath.money(cost), reason, today, clock.now(), SecurityUtils.currentUserId().orElse(null)));
        PostingContext ctx = new PostingContext(StockSourceType.STOCK_ADJUSTMENT, adj.getId(), number, today, reason);
        if (type.isInbound()) {
            posting.receive(type.movementType(), product.getId(), quantity, cost, ctx, null);
        } else {
            posting.issue(type.movementType(), product.getId(), quantity, ctx, null);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", type.name());
        details.put("product", product.getName());
        details.put("quantity", quantity.stripTrailingZeros().toPlainString());
        details.put("reason", reason);
        audit.log(AuditEvent.of(AuditAction.ADJUST, AuditModule.INVENTORY, "StockAdjustment", adj.getId(), number, "Stock adjustment " + type)
                .withDetails(details));
        return adj;
    }

    @Transactional(readOnly = true)
    public Page<AdjustmentResponse> search(Long productId, AdjustmentType type, LocalDate from, LocalDate to, String q, Pageable pageable) {
        Specification<StockAdjustment> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (productId != null) ps.add(cb.equal(root.get("productId"), productId));
            if (type != null) ps.add(cb.equal(root.get("adjustmentType"), type));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("adjustmentNo")), like), cb.like(cb.lower(root.get("reason")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<StockAdjustment> page = adjustments.findAll(spec, pageable);
        Map<Long, Product> productMap = queries.productMap(page.getContent().stream().map(StockAdjustment::getProductId).toList());
        Map<Long, String> names = userDirectory.names(page.getContent().stream().map(StockAdjustment::getCreatedBy).toList());
        return page.map(a -> toResponse(a, productMap.get(a.getProductId()), names.get(a.getCreatedBy())));
    }

    private AdjustmentResponse toResponse(StockAdjustment a) {
        Product p = products.findById(a.getProductId()).orElseThrow();
        return toResponse(a, p, userDirectory.names(List.of(a.getCreatedBy() == null ? -1L : a.getCreatedBy())).get(a.getCreatedBy()));
    }

    private static AdjustmentResponse toResponse(StockAdjustment a, Product p, String createdByName) {
        boolean showCost = InventoryQueryService.canSeeCost();
        return new AdjustmentResponse(a.getId(), a.getAdjustmentNo(), p.getId(), p.getName(), p.getCode(), p.getUnit().getCode(), p.getUnit().getName(),
                a.getAdjustmentType(), a.getQuantity(), showCost ? a.getUnitCostBase() : null,
                showCost ? StockMath.money(a.getQuantity().multiply(a.getUnitCostBase())) : null, a.getReason(), a.getBusinessDate(), a.getCreatedAt(),
                createdByName);
    }
}
