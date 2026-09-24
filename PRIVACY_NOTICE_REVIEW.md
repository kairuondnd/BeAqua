# Signup privacy notice — draft, 24 September 2026

The signup notice and acknowledgment UI are implemented for customers and station owners.
This draft is not a declaration of legal compliance and is not ready to be represented as a final privacy policy.

## Operator decisions required before public release

Complete `app/src/main/res/values/privacy_notice.xml` with:

- The actual person or organization responsible for BeAqua (`privacy_operator`).
- A monitored privacy contact email (`privacy_contact_email`).
- Retention periods or criteria for accounts, orders/receipts, chats, permits and backups, and an actionable deletion process (`privacy_retention_policy`).
- The applicable legal basis for each processing purpose, including any separate consent needed (`privacy_processing_basis`).

Verify service-provider arrangements, actual database/storage access rules and hosting/transfer details against the deployed services. Review the notice against those settings. Confirm a process for access, correction, deletion and other rights requests. Update `PrivacyNotice.VERSION` whenever the text is changed or finalized. Do not remove the draft label without completing this review.

## Confirmed from the current implementation

- Signup collects the fields listed in the notice. The chosen address and map coordinates are stored; map search uses Mapbox.
- Firebase holds app records. New station permits use `FirestoreKycDocumentStore`; product images and payment QR uploads use Firebase Storage.
- Existing legacy permits may still refer to Supabase; verify whether those records remain in the deployed database.
- Customer–station chat and feedback are stored. Order handling exposes the delivery information needed by the station. Administrator screens can read user records and station documents.
- `deleteUser` deletes only the user document. It does not cascade deletion to orders, messages, uploaded permits or images; no automatic retention schedule was found.
- **Customer passwords are currently saved and compared as plaintext.** Station-owner signup hashes passwords, but customer signup does not. Address customer authentication and review deployed access controls before claiming credentials are securely protected.

## Acknowledgment

The unchecked signup checkbox records `privacyNoticeVersion` and `privacyNoticeAcknowledgedAt` on a newly created user. The timestamp comes from the device clock and is not a trusted server audit timestamp. Older accounts keep empty/zero defaults; their acknowledgment is not fabricated. This is an acknowledgment of reading, not consent for unrelated uses or a waiver of rights.

## References

- National Privacy Commission: https://privacy.gov.ph/the-right-to-be-informed/
- NPC consent guidance: https://privacy.gov.ph/wp-content/uploads/2023/11/NPC-Circular-No.-2023-04_Guidelines-on-Consent_07Nov2023.pdf
