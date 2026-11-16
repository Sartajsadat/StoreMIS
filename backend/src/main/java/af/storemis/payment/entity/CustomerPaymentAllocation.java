package af.storemis.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Part of a payment applied to one invoice. applied = in the invoice currency; paid = in the payment currency;
 * crossRate = invoice-currency units per payment-currency unit (1 when the currencies match).
 * released = the sale was cancelled and this money was refunded or turned into customer credit.
 */
@Entity
@Table(name = "customer_payment_allocations")
public class CustomerPaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private CustomerPayment payment;

    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long saleId;

    @Column(name = "applied_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "paid_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "cross_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal crossRate;

    @Column(name = "released", nullable = false)
    private boolean released;

    protected CustomerPaymentAllocation() {}

    public CustomerPaymentAllocation(CustomerPayment payment, Long saleId, BigDecimal appliedAmount, BigDecimal paidAmount, BigDecimal crossRate) {
        this.payment = payment;
        this.saleId = saleId;
        this.appliedAmount = appliedAmount;
        this.paidAmount = paidAmount;
        this.crossRate = crossRate;
    }

    public void release() {
        this.released = true;
    }

    public Long getId() {
        return id;
    }

    public CustomerPayment getPayment() {
        return payment;
    }

    public Long getSaleId() {
        return saleId;
    }

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getCrossRate() {
        return crossRate;
    }

    public boolean isReleased() {
        return released;
    }
}
