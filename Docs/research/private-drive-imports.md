# Private Google Drive imports and the cloud-provider boundary

Research for [issue #4](https://github.com/A3R0-01/Cloud-Epub/issues/4), checked 7 October 2026. This is a researched proposal, not a chosen implementation or a tested Android integration.

## Answer

Google Drive imports can run entirely on Android: select a source file, obtain its bytes, validate the EPUB locally, encrypt its contents and library metadata, then upload ciphertext to the app's independent backend. The app backend needs neither a Google email address nor Google credentials. Copying is an application workflow; no Drive-to-backend server copy is needed. Google retains its existing visibility into the source account/file; encryption of the imported copy does not retroactively protect the original.

Two supported selection routes fit this boundary. Android's Storage Access Framework (SAF) is the simplest general file importer. Google's current Android Picker plus `AuthorizationClient` supports a distinct Google Drive import action with per-file authorization. These differ mainly in user experience, dependencies, and operational setup, rather than encryption design. Keep the route choice provisional until the picker behavior is demonstrated on supported devices.

## Current primary-source facts

### Android's system document picker

SAF exposes local and cloud document providers through a system picker; Android documentation explicitly uses Google Drive as a provider example. A client receives a selected document URI rather than provider credentials. The picker lists matching registered providers, so it does not guarantee that Drive appears on every device. [Android SAF overview](https://developer.android.com/guide/topics/providers/document-provider)

Use `ACTION_OPEN_DOCUMENT`, `CATEGORY_OPENABLE`, and an EPUB MIME filter, then open the returned URI through `ContentResolver`. User selection avoids broad storage permissions. A file's reported size can be unknown. Persistable URI grants support later access, but moving/deleting the source can invalidate them. Treat the URI as opaque; a grant is not a local-file guarantee. Virtual documents need alternate representations and are unsuitable for a simple raw EPUB importer. [Android document access](https://developer.android.com/training/data-storage/shared/documents-files)

**Design inference:** use read access only, copy into app-owned storage, and release any persisted grant when the import no longer needs the source. If persistence is necessary for a queued retry, take only flags actually granted. Do not request whole-folder access or convert arbitrary virtual documents into EPUBs.

### Google Picker on Android, not only a web picker

Google's mobile Picker guide, updated 29 September 2026, documents Android integration using `AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER`. Request `drive.file`, set `setOptOutIncludingGrantedScopes(true)`, and request `CONSENT`; account selection can also be requested. Picker MIME filters and multiple selection are supported. Selected file IDs are returned under `picked_file_ids` in `AuthorizationResult.getTokenResponseParams()`. The guide requires Google Cloud/OAuth setup and enabling the Picker API; downloads also use the Drive API. [Current native/mobile Picker guide](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker), [resource parameter API](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationRequest.ResourceParameter)

**Design inference:** an explicit “Import from Google Drive” button can use this Google-owned selection UI without building a full Drive browser or sending tokens to the app backend. Do not assume old web-only Picker advice remains current. Pin the actual dependency supporting these APIs during implementation; documentation verification does not prove availability on the project's device matrix.

### Scopes and selection limits

`drive.file` is a non-sensitive, per-file scope covering app-created files and files opened/shared with the app. It includes write capabilities for authorized files; it is not read-only. Broad `drive`, `drive.readonly`, and `drive.metadata.readonly` are restricted scopes with additional verification requirements; Google documents a security assessment when restricted-scope data is stored or transmitted on servers. [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)

**Design inference:** the import adapter must perform reads only despite the scope's broader per-file capabilities. `drive.file` does not grant whole-Drive listing: an app-owned browser cannot discover every pre-existing EPUB merely by calling `files.list` with that scope. Use Picker to obtain grants for selected files. A SAF URI grant also must not be assumed to create a Drive API `drive.file` grant; those are separate access mechanisms. No broad restricted scope is necessary for the proposed v1 flow.

### Authorization, identity, and token lifecycle

Google distinguishes app authentication from authorization to Google data. Android recommends `AuthorizationClient` for the latter. Its authorization result does not inherently include the selected account's name/email; additional identity flows/scopes can expose those. Access tokens last about one hour; subsequent `authorize()` calls can obtain tokens without user interaction while the grant remains valid. Server “offline access” obtains an authorization code for backend refresh-token exchange; the guide discourages device refresh-token storage. Revocation clears grants/cached tokens, and invalid tokens can be cleared from cache. [Android authorization](https://developer.android.com/identity/authorization)

**Design proposal:** no Google account sign-in for the app account, no identity scopes/userinfo requests, no `requestOfflineAccess`, and no backend Google token exchange. Keep short-lived access tokens inside the Android adapter and out of app logs, analytics, sync records, crash attachments, and persistent import metadata. Obtain authorization again when necessary. A successful authorization is not proof that the selected file remains readable.

### Downloads and disconnected operation

A binary EPUB on Drive is downloaded with `files.get(fileId, alt=media)`; check `capabilities.canDownload` first. Drive supports partial/range downloads. Google Workspace documents use export instead and are not raw EPUB inputs. [Drive download guide](https://developers.google.com/workspace/drive/api/guides/manage-downloads)

Drive documents error cases including missing access, deleted files, rate limits, and server failures, and recommends exponential backoff for retryable cases. [Drive errors](https://developers.google.com/workspace/drive/api/guides/handle-errors)

The Drive app offers user-controlled offline availability. That feature is not a promise that Cloud EPUB can read every selected Drive URI offline. Android also defines a partial-document flag for content not fully available locally. [Drive offline help](https://support.google.com/drive/answer/2375012?co=GENIE.Platform%3DAndroid&hl=en), [document flags](https://developer.android.com/reference/android/provider/DocumentsContract.Document#FLAG_PARTIAL)

**Design proposal:** source retrieval and backend upload are distinct phases. If all source bytes are already available, finish local import offline and queue an encrypted upload. A new API download normally requires network access. SAF may succeed from cached/provider-local bytes, but report a recoverable source-unavailable state if it cannot. Never show “Backed up” before backend completion. Previously imported app-owned copies remain readable after Drive disconnection, source edits, deletion, or revocation.

## Minimal client boundary

The following interface responsibilities are proposals, independent of SDK, backend vendor, EPUB engine, or encryption algorithm:

| Boundary | Responsibility | Data leaving this boundary |
| --- | --- | --- |
| Import source adapter | Show provider/system selection, manage provider authorization, open a cancellable byte stream, report unavailable/denied/cancelled/transient errors | Android-local opaque source handle, optional display name/MIME/size, readable bytes |
| Library import coordinator | Apply size/archive limits, validate supported EPUB locally, stage a stable independent copy, extract title/cover, report progress | Local publication and import state |
| Encryption/key component | Encrypt EPUB, title, cover, source provenance if retained, bookmarks, progress, and other private library fields | Versioned authenticated ciphertext envelopes and opaque IDs |
| Backend library transport | Upload/download ciphertext, retry/commit transfer, enforce account ownership/quota, synchronize encrypted records | Ciphertext and necessary operational metadata |
| Reader | Open the local trusted publication; create layout-independent reading positions and bookmarks | Local data passed to encryption/sync |

Expose selection and byte acquisition rather than `listEntireCloud`, provider tokens, or remote-copy methods. Source adapters need not promise seeking, known size, or resumability; the coordinator stages a stable copy before an engine that requires random access opens it. Make retry/reopen support optional. Distinguish an Android `content://` handle from a Drive file ID inside each adapter. These source references must not become public backend object names.

V1 may use one SAF adapter for both device files and Drive when available. If a separate Drive action is required, add a Google Picker/Drive-download adapter using the same boundary. A later service can provide another picker/download adapter, or work automatically through SAF if it exposes a document provider. Do not require every future service to share Google's token lifecycle.

Suggested import states: selecting, reading source, validating locally, available locally, queued encrypted upload, uploading, backed up, cancelled, and failed with a retry action. A queued upload uses the app-owned copy, not a promise to re-download the source. Exact archive quotas, staging cleanup, authenticated chunk encryption, key recovery, conflict semantics, and Android background scheduling remain decisions for their respective specifications.

## Honest privacy boundary

The Android app and EPUB engine must access plaintext to display it. The device, relevant source provider, and already-existing Drive source are outside the ciphertext-only backend boundary. The backend still sees an app account identifier, ciphertext size, request timing, network information, and any operational metadata intentionally exposed; this proposal is data minimization, not network anonymity. Do not label the entire system anonymous merely because emails are omitted.

Google's privacy policy describes collection of account-associated content and activity, device/browser information, IP addresses, and interaction times. Google authentication/authorization necessarily involves Google; its account identity does not disappear because Cloud EPUB declines to receive/store the email. [Google privacy policy](https://policies.google.com/privacy)

**Design proposal:** use random backend object IDs, encrypt title/cover/provenance and reading records, minimize logs and retention, and disclose operational leakage. Audit Android backups and caches separately. Avoid external covers, remote book resources, analytics, or crash reporting that leak titles, selections, tokens, or plaintext. Encrypting uploads does not authorize silently uploading an entire Drive library: import remains a user-selected action.

## Provisional recommendation and acceptance checks

Start with SAF if “pick a Drive EPUB through Android's file picker” meets the requested UX. Choose the native Google Picker route if dedicated Drive browsing without depending on a visible Drive documents provider is a v1 requirement. Both preserve local encryption and independent copies. Do not finalize this choice without testing the targeted Android versions, Google Play services dependency, Drive account/provider availability, misreported MIME types, cancellation, and source revocation.

Implementation acceptance should demonstrate:

1. Import from local storage and a real Drive account; validate bytes even when metadata is incorrect or size unknown.
2. Cancel selection/download/upload; no completed duplicate or partial readable library entry survives an abandoned import.
3. Distinguish denied access, removed source, missing provider, no network, malformed EPUB, exhausted local storage, and backend failure; retries never silently choose another file.
4. Read an imported offline copy after deleting the original Drive file and after revoking Drive authorization.
5. Queue encrypted backend upload while offline and resume without Drive tokens/source access once the local copy exists; final commit is idempotent.
6. Inspect network requests, backend records, logs, and object names: no plaintext EPUB/title/cover/bookmark/progress, Google email, source ID, OAuth code, or Google token reaches the app backend.
7. For the API route, demonstrate `drive.file` selection of a pre-existing EPUB through the current Android Picker without adding restricted or identity scopes.
8. Add a fake alternate source adapter in an implementation test and prove the common import/encryption/transport pipeline needs no provider-specific branches.

These checks are a proposed validation plan. No app code, device trial, Google Cloud project, cost commitment, or API deployment was produced by this research.
