# MT103 – Single Customer Credit Transfer: field reference

MT103 is the SWIFT FIN message a bank sends to move a customer's payment to another bank,
usually cross-border. The payment details live in block 4 (the text block), one field per tag.

## Mandatory fields

- **:20: Sender's Reference** – up to 16 characters chosen by the sending bank. It must not
  start or end with a slash `/` and must not contain two consecutive slashes `//`.
- **:23B: Bank Operation Code** – one of CRED (normal credit transfer), CRTS (test message),
  SPAY, SPRI or SSTD (SWIFTPay service levels). CRED is by far the most common.
- **:32A: Value Date / Currency / Interbank Settled Amount** – format `YYMMDD` + 3-letter ISO 4217
  currency + amount. The amount uses a comma as decimal separator, e.g. `261030USD1250,50`.
  The integer part must have at least one digit and the comma is mandatory.
- **:50a: Ordering Customer** – the payer. Option A (BIC), F (structured party identifier with
  numbered lines) or K (optional `/account` line, then up to 4 lines of name and address,
  35 characters each).
- **:59a: Beneficiary Customer** – the payee. No letter option (optional `/account` line, then
  name and address), option A (BIC) or F (structured).
- **:71A: Details of Charges** – who pays the transfer fees:
  - `OUR` – the ordering customer pays all charges.
  - `SHA` – charges are shared: the sender pays its own bank, the beneficiary pays the rest.
  - `BEN` – the beneficiary pays all charges, deducted from the amount.

## Common optional fields

- **:33B: Currency / Instructed Amount** – the original amount before charges or currency conversion.
- **:52A: Ordering Institution** – the payer's bank, identified by BIC.
- **:53a / :54a** – sender's and receiver's correspondents (nostro accounts used for cover).
- **:56A: Intermediary Institution** – a bank between the sender and the beneficiary's bank.
- **:57A: Account With Institution** – the beneficiary's bank, identified by BIC.
- **:70: Remittance Information** – free text for the beneficiary (invoice number etc.), up to 4 lines of 35.
- **:71F: Sender's Charges** – charges deducted by banks in the chain. Mandatory (at least once) when 71A is BEN.
- **:71G: Receiver's Charges** – charges the sender pre-pays to the receiver. Only allowed when 71A is OUR.
- **:72: Sender to Receiver Information** – bank-to-bank instructions, often with codes like `/INS/` or `/ACC/`.

## BIC format

A BIC (ISO 9362) is 8 or 11 characters: 4 letters for the bank, 2 letters for the country,
2 letters or digits for the location, and an optional 3-character branch code.
Example: `HDFCINBBXXX`. In fields like 52A and 57A an optional `/account` line can come first;
the BIC is on the last line.

## Frequent reject causes

- Amount written with a dot (`1250.50`) instead of a comma (`1250,50`).
- 71A = BEN with no 71F, or 71G present when 71A is SHA or BEN.
- Name and address lines longer than 35 characters, or more than 4 lines.
- Invalid or unknown BIC in 52A / 57A.
- Sender's reference containing `//` or starting/ending with `/`.
