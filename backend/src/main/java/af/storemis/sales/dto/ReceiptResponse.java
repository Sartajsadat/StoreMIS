package af.storemis.sales.dto;

/** Everything a printed invoice/receipt needs, available to anyone who may see the sale. */
public record ReceiptResponse(SaleResponse sale, Business business) {

    public record Business(String name, String address, String phone, String email, String logoUrl, boolean showLogo, String receiptFormat,
            String headerNote, String footerNote, String baseCurrency) {}
}
