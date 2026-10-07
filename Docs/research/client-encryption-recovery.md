# Client-side encryption, username authentication, and recovery

Research for [Research client-side encryption, username authentication, and recovery codes](https://github.com/A3R0-01/Cloud-Epub/issues/3), part of [Find the build-ready specification for Cloud EPUB Android and backend](https://github.com/A3R0-01/Cloud-Epub/issues/1). Checked 7 October 2026. This resolves the research question; it does **not** select the final security design or claim an implementation audit.

## Confirmed product boundary

Version one has a username and password, without email or phone fields. EPUB bytes, titles, bookmarks, and reading progress must be encrypted on Android before upload; the backend must not receive their decryption keys. Google Drive import makes an independent library copy. Six-month expiration counts authenticated app use and synchronization. Recovery through a long random code is proposed; exact mechanisms remain undecided.

The feasible result is a pseudonymous service with encrypted content. It cannot promise complete anonymity: the service must associate requests and stored objects with an account to authenticate, enforce quotas, synchronize, and expire it. Infrastructure sees connections and operational metadata. These are implications of the requested product, not permission to collect contact details.

## Separate five responsibilities

| Responsibility | Proposed ownership | Why it must be separate |
| --- | --- | --- |
| Account authentication | Server verifies credentials/protocol and issues sessions | Permission to fetch ciphertext does not imply ability to decrypt it. |
| Library encryption | Client generates a random library key; optional independent per-book keys | The data key should outlive password changes. |
| Unlock/key wrapping | Client encrypts the library key under an unlock key | Password updates can replace a small key envelope rather than every EPUB. |
| Account recovery | Recovery credential authorizes replacement of account authentication | The server must validate authority without receiving a data-decryption secret. |
| Key recovery | Client uses a separately protected recovery envelope | Resetting a login password alone cannot restore unavailable encryption keys. |

These are architectural proposals, requiring a concrete reviewed protocol profile. Do not combine a server password hash, a bearer session token, a library key, and a recovery secret into one value.

Established primitives support this separation. Libsodium provides distinct password-verification and password-to-key APIs, with salts and versioned cost parameters; Argon2id is supported. Parameters must be stored and benchmarked on the actual Android devices, rather than copying desktop timings. [Libsodium password hashing](https://doc.libsodium.org/password_hashing/default_phf)

Authenticated encryption protects ciphertext integrity as well as confidentiality and can bind non-secret context as associated data. A candidate record envelope would bind account/object identifiers, type, format version, and key version. This prevents accidental cross-object substitution; freshness/rollback needs a separate version policy. [Libsodium AEAD](https://doc.libsodium.org/secret-key_cryptography/aead)

For whole EPUB uploads, libsodium's secretstream API handles chunked authenticated encryption and manages nonces. It detects invalid stream alterations; successful import must require the final authenticated end marker. It is sequential: random-access EPUB reading still needs a local seekable representation or a different reviewed chunk format. Whole-file decrypt-on-download is the simpler candidate for v1, subject to size/memory/storage limits. [Libsodium file encryption](https://doc.libsodium.org/secret-key_cryptography/secretstream)

## Authentication alternatives

### A. OPAQUE plus a client-side library-key envelope

OPAQUE hides the password from the server, including registration, and yields a client-only `export_key` alongside a shared session key. Use the export key only after peer authentication. It is suitable input for protecting application data; the shared session key is **not** a client-only library key. OPAQUE still permits password guessing after server compromise and requires key stretching. RFC 9807 is a CFRG/IRTF Informational RFC, not an Internet Standards Track standard. [RFC 9807](https://www.rfc-editor.org/rfc/rfc9807.html)

Proposal: generate a random library key on first signup, protect it in a client-side envelope under an export-key-derived wrapping key, and upload that envelope. On another device, successful online authentication obtains the appropriate export key and ciphertext envelope. This provides the desired one-password online experience without sending that password to conventional backend password verification.

Costs/gaps: specify the exact configuration, serialization, identity/context binding, state machine, rate limits, and failure handling. Cache a device-protected library key for offline reading; a new device cannot run an online protocol while offline. Password re-registration changes the relevant wrapping material, so the old key must already be unlocked or recovered before atomically replacing its envelope. Protect this transaction against interruption and concurrent devices.

Library evidence is promising but not a shipping decision. `facebook/opaque-ke` is Rust, advertises RFC 9807 support, and its inspected README recommends `4.1.0-pre.2`. It reports a 2021 NCC Group audit of an earlier release; that does not establish audit coverage for current RFC implementation or Android bindings. `bytemare/opaque` is Go, implements RFC 9807, and explicitly reports no independent audit. [opaque-ke](https://github.com/facebook/opaque-ke), [bytemare/opaque](https://github.com/bytemare/opaque)

An Android/server interoperability spike must pin releases, run published test vectors, prove native integration, verify export-key handling, and measure KDF latency/memory. External security review of the composed registration/recovery/key-envelope protocol remains necessary. A standards reference alone does not supply a reviewed application protocol.

### B. Conventional username/password authentication plus an independent encryption secret

A simpler backend can receive the login password over TLS and store a salted memory-hard verifier. To preserve the confirmed privacy boundary, the library unlock secret must then be independent and remain client-only. It could be a separate encryption passphrase or a random secret provisioned from an existing device/recovery package. The user would need additional secret handling on a new device.

**Reject the tempting shortcut:** using the same password for ordinary server login and local library-key wrapping lets that server derive the wrapping key from the received password and stored parameters. Merely hashing the stored password or encrypting transport does not fix this. This is a direct design inference from password-based key derivation, not an acceptable end-to-end privacy scheme.

A client-side hash submitted as the login credential is itself replayable credential material; do not improvise a double-hash protocol. Passkeys could eventually replace account authentication, but do not automatically solve portable library-key recovery. These alternatives should be evaluated only if their extra user interactions are acceptable.

### C. One entered password, separate client-derived authentication and encryption material

This is a real established pattern, not automatically disqualified by B's warning. Bitwarden documents client-derived authentication material sent to the server, separate locally retained wrapping material, and a random encrypted account data key. The server hashes the received authentication value again. Its master password and encryption key remain client-side. [Bitwarden security whitepaper, hashing/key derivation](https://bitwarden.com/help/bitwarden-security-white-paper/#hashing-key-derivation-and-encryption)

Candidate C would preserve one-password UX by using a reviewed client derivation/wrapping scheme with explicit separation of purposes. Do not simply transplant Bitwarden's email salt, algorithm choices, or recovery features: our account identifier, no-contact requirement, and recovery-code semantics differ. Pin an actual vetted protocol/library or commission review of that adaptation. A generic KDF's correctness does not audit this composition.

The backend receives a password-equivalent credential, not a decryption key. This can be simpler than PAKE integration, but a credential disclosure permits account access/replay, and a compromised or actively guessing server can test password candidates against its authentication material or stored envelope. Strong password policy and memory-hard derivation mitigate guessing rather than eliminate it. TLS remains necessary.

Research recommendation: compare A's protocol/integration cost, B's extra encryption-secret burden, and C's more conventional credential flow plus adaptation/guessing risks before choosing. None should be silently selected for the user.

### Threat model required for all choices

* An honest service following the chosen protocol should store only ciphertext and verification/wrapped-key material; it should not have plaintext data keys.
* A database theft enables attacks on credentials/envelopes. Weak passwords remain a risk; neither AEAD nor a PAKE makes guessable passwords unguessable. Distinguish a stolen database from compromise of all authentication-server secrets.
* An active malicious backend can deny access, delete ciphertext, return stale versions, and manipulate unauthenticated protocol/configuration data. Bind authenticated identities/parameters and define client downgrade/rollback handling. OPAQUE's export-key property is not protection against every application-layer behavior.
* A malicious app build/update or compromised unlocked device can capture a typed password, recovery secret, plaintext book, or cached key. A native app reduces dependence on server-supplied runtime code, but signed updates and the client supply chain remain trusted. End-to-end encryption cannot hide data from the endpoint actively rendering it.

These are analysis of the proposed trust boundaries. They must become explicit promises/limitations in the specification; do not state an unconditional guarantee against all malicious operators or endpoints.

## Recovery code design and lifecycle

NIST treats saved recovery codes as authenticators: random, stored offline, server-stored in hashed form, rate limited, and replaced after use. Its minimum authentication-code entropy is not a library-encryption-key design target. The guidance is useful engineering input here; this service has not been assessed for NIST assurance-level conformity, and omitting contact channels changes its notification options. [NIST SP 800-63B-4, saved recovery codes](https://pages.nist.gov/800-63-4/sp800-63b.html#saved-recovery-codes)

Proposal: generate a high-entropy recovery secret on the device (for example, 256 random bits, not user-chosen prose). Show a copyable/printable recovery package with account identifier and version. Keep the original secret out of backend requests, logs, crash reports, and routine app storage. Having a checksum for typing mistakes adds usability, not entropy.

One package can potentially serve both recovery jobs through separately derived material. An established KDF can derive independent subkeys from a high-entropy master with separate contexts; possession of one subkey does not reveal the others. [Libsodium key derivation](https://doc.libsodium.org/key_derivation)

The **application composition still needs review**: a recovery-authentication credential/proof must authorize server replacement, while a different recovery-wrapping key decrypts the library-key envelope locally. Do not send the root recovery secret or the wrapping key to the server. Candidates are a separate high-entropy bearer credential whose verifier is stored hashed, or an established proof-of-possession mechanism. Select and document one concrete protocol in a later security decision; this note does not invent its message flow.

Proposed lifecycle acceptance scenarios:

| Event | Required outcome / unresolved detail |
| --- | --- |
| Signup | Generate library key and recovery package; confirm the user saved it before presenting the account as safely recoverable. |
| New device | Authenticate, retrieve encrypted envelopes, unlock locally; do not require another user to disclose their secret. |
| Password change | Authenticate recently, unlock the existing library key, create new authentication material and wrapping envelope atomically; revoke old sessions according to chosen policy. |
| Forgotten password + code | Prove recovery authority and decrypt locally; replace password authentication/envelope, invalidate recovery authorization, issue a new package, and revoke sessions. |
| Lost code, password still works | Let an authenticated, unlocked client replace the package. Losing the code does not immediately lose the library. |
| Lost password and code, unlocked device remains | A device-assisted recovery route is technically possible; decide whether that device has reset authority. Local possession of data alone is not automatically account authority. |
| All unlock/recovery paths lost | Existing encrypted library cannot be restored by support. Decide whether login reset with a new empty library exists; do not imply it recovers the old one. |
| Code stolen | Rotate recovery authorization and envelopes; decide whether compromise requires rotating the library key and re-encrypting data. Removing an envelope cannot retract copies already obtained. |

Library-key rotation differs from recovery-code rotation. A thief with an old code and copied recovery envelope can retain the old library key; credential revocation only prevents authorized future server access. Strong revocation against copied ciphertext requires changing data keys and considering old versions/backups. This follows from the envelope model.

**Do not delete an account because the secret was lost.** The service cannot observe someone losing a paper or text file. An unauthenticated statement of loss proves no deletion authority and would enable denial of service. Explicit deletion requires authenticated authority; inactivity expiration is a separate server policy.

## Android, imports, and remaining metadata

Android Keystore supports non-exportable app keys and optional device-authentication constraints. Hardware protection depends on the device and algorithm; compromised app code may still use a key. A device-local Keystore key can protect the cached library key, while the portable library key remains recoverable through remote envelopes. Biometric enrollment can invalidate appropriately configured keys. Decide the offline unlock policy and test device reset, reinstall, biometric changes, and recovery. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

EPUB rendering requires decrypted text/images somewhere on the device. Include temporary extraction files, local databases, caches, screenshots, analytics, and error reports in the design review. Parse EPUB content as untrusted input; encryption does not make imported HTML safe. If the backend must never read a book, EPUB parsing, title/cover extraction, search, and Drive-download encryption must happen on Android. Google Drive still knows its own user and files; separating Drive authorization from app authentication does not hide those from Google.

Android backs up most app data by default. Cloud backup and device-transfer rules differ, including Android 12 manufacturer behavior; `allowBackup=false` alone is not a complete transfer exclusion. Explicitly exclude plaintext books, decrypted metadata, tokens, recovery secrets, and device-only wrapped material where applicable, and test restore behavior. [Android Auto Backup](https://developer.android.com/identity/data/autobackup)

Expected backend visibility includes username/account ID, authentication records, random object IDs, ciphertext lengths/counts, request times, last activity, sessions, storage usage, and network IPs. Avoid plaintext filenames, unencrypted titles/bookmarks, globally comparable book hashes, or book identities in URL paths/logs. A random ID plus client-encrypted metadata is the candidate. Padding and traffic-hiding are distinct, more costly features; neither is currently promised.

## Six-month inactivity and honest deletion

Proposed policy for the decision ticket: use server-received qualifying authenticated activity to set `last_active_at`; calculate an explicit expiration instant and define whether six months means calendar months or fixed days. Return that deadline to the app. Offline reading cannot update the server; users need to connect before expiry or accept cloud-account deletion despite continuing local reading. An in-app warning cannot reach a device that never reconnects. No email channel exists.

The deletion worker must atomically recheck activity/expiry, transition the account to deletion, revoke sessions, cancel imports/uploads, prevent further writes, and remove live ciphertext/envelopes/authentication records. Decide grace duration, reconnect behavior at the boundary, username reuse, and whether local downloads remain usable. A late sync must not resurrect a deleted account or silently upload old private content into a new account with the same username.

Physical backup retention is provider-specific. Specify a finite retention window, delete object versions/replicas where supported, and ensure restores replay deletion records before serving users. Minimal deletion records may need retention to enforce that promise; explain them transparently.

NIST's cryptographic erasure guidance depends on effective elimination of relevant keys and copies, including hierarchy and recoverability considerations. [NIST SP 800-88 Rev. 2](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-88r2.pdf)

For this proposal, deleting the live recovery/password envelopes does **not** establish immediate cryptographic erasure: backups can retain envelopes and ciphertext, users retain keys/codes, and already downloaded copies remain. A provider-only per-account outer encryption layer with independently erasable keys might improve backend-backup erasure without replacing client encryption, but adds key-management cost and needs a verified provider design. Do not promise global or instantaneous erasure. State separate guarantees for immediate access revocation, deletion from live service, backup expiry, and user-controlled local copies.

## Decisions and implementation spikes still needed

1. Choose authentication A, B, or C, permitted account/device recovery paths, and the threat model for a compromised backend versus compromised Android/app updates.
2. Pin and validate crypto libraries, wire profile, key envelopes, nonce/associated-data rules, password policy/KDF costs, recovery format, and rotation transactions through a bounded interoperability spike and security review.
3. Decide offline unlock/temporary plaintext/backup rules and encrypted synchronization freshness/conflict behavior. AEAD integrity does not establish that the server returned the latest state.
4. Resolve expiration duration, warning/grace UX, boundary races, local-copy behavior, backup retention, and deletion guarantees with the selected provider.
5. Make these behaviors acceptance tests in the final specification: new-device unlock, reset preserving books, code replacement, corruption rejection, interrupted key updates, expired-account sync rejection, and deletion after backup restore.
