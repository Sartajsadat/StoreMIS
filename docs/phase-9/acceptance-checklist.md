# Store MIS — Acceptance Test Checklist

Run this with the shop owner before going live. Use a fresh installation, then restore a clean backup (or
reinstall) before entering the real opening balances. Tick each line when the result matches.

Test data: USD rate 70 AFN; products "LED TV" (cost 3,000 AFN, 10 pcs, minimum 5) and "Fan" (cost 900 AFN,
6 pcs, minimum 5); customer "Ahmad" (wholesale); supplier "Kabul Traders"; AFN cash 100,000, USD cash 1,000.

## A. Sign-in, users and languages

| # | Action | Expected | ✓ |
|---|---|---|---|
| A1 | First sign-in as admin | Asked to change the password; afterwards the dashboard opens | |
| A2 | Enter a wrong password 5 times for a test user | Account locked for 15 minutes | |
| A3 | Create an Entry User with only "Give discount" | User can sell with a discount, cannot see Reports, Finance or cost prices | |
| A4 | Switch to دری, then پښتو | Whole screen right-to-left, menus and numbers correct; choice kept after signing in again | |
| A5 | Dark theme | All pages readable | |

## B. Products and inventory

| # | Action | Expected | ✓ |
|---|---|---|---|
| B1 | Create a product with opening stock | Stock and average cost shown; stock movement "Opening" | |
| B2 | Sell until a product is at its minimum | Bell shows a low-stock alert; product in Low Stock | |
| B3 | Damage adjustment of 1 unit | Stock −1; appears in Damaged stock report and as inventory loss in P&L | |

## C. Purchases and suppliers

| # | Action | Expected | ✓ |
|---|---|---|---|
| C1 | Purchase 10 Fans at 800 AFN, pay 2,000 | Stock +10, new average cost; supplier owes 6,000; AFN cash −2,000 | |
| C2 | Purchase in USD at rate 70 | Cost converted at 70; supplier balance in USD | |
| C3 | Pay the supplier 6,000 AFN | Supplier balance 0; purchase Paid | |
| C4 | Return 2 Fans to the supplier | Stock −2; supplier balance / refund correct | |

## D. Sales and customers

| # | Action | Expected | ✓ |
|---|---|---|---|
| D1 | Walk-in sale paid in cash, print receipt | Receipt prints correctly on the thermal printer | |
| D2 | Sale of 10,000 AFN to Ahmad, 6,000 paid | Customer owes 4,000; invoice Partially Paid | |
| D3 | Receive 4,000 later | Customer balance 0; invoice Paid | |
| D4 | Sale of 14,000 AFN paid 100 USD + 7,000 AFN | Both cash accounts grow in their own currency; invoice Paid | |
| D5 | Hold a cart, open it again, complete it | Sale saved once | |
| D6 | Return 1 item as Sellable with cash refund | Stock back; cash −refund; receivable reduced first | |
| D7 | Return 1 item as Damaged | No stock back; loss in P&L | |
| D8 | Admin cancels a sale without returns | Stock back; payment refunded or kept as credit | |

## E. Finance

| # | Action | Expected | ✓ |
|---|---|---|---|
| E1 | Record rent 8,000 AFN with a receipt photo | AFN cash −8,000; receipt opens | |
| E2 | Void an expense | Cash restored; expense shown as Voided | |
| E3 | Exchange 100 USD → 7,050 AFN | USD −100, AFN +7,050 | |
| E4 | Owner deposit and withdrawal | Cash changes; not in P&L | |
| E5 | Daily closing with 100 AFN short | Difference posted; day locked; a new sale/expense is refused | |
| E6 | Reopen the day (Admin, with reason) | Postings allowed again; reopen in activity log | |

## F. Dashboard and reports

| # | Action | Expected | ✓ |
|---|---|---|---|
| F1 | Dashboard, period Today | Net sales, profit, expenses, cash and alerts match the day's work | |
| F2 | Profit and loss for the test period | Matches a hand calculation: net sales − COGS − expenses − losses | |
| F3 | Each report tab under Reports | Opens without errors; totals make sense | |
| F4 | Excel export | Opens in Excel; numbers can be summed; Dari export is right-to-left | |
| F5 | PDF export (Save as PDF) | Readable PDF in English and دری | |
| F6 | Entry user's dashboard | "My Day" with only their own sales | |

## G. Operations

| # | Action | Expected | ✓ |
|---|---|---|---|
| G1 | Back up now | New folder in `backups/daily`; `last-backup.txt` says OK; copy on the USB disk | |
| G2 | Change something, then restore the backup | Data is back as it was at the backup; logo/images present | |
| G3 | Switch the server off and on | Store MIS comes back by itself within ~2 minutes | |
| G4 | Unplug the network of a shop PC during a sale, plug back, retry | No duplicate sale | |
| G5 | Two PCs sell the last unit at the same time | One sale succeeds, the other gets "not enough stock" | |

Signed off by (owner): ____________________  Date: ____________
