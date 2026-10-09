# Dictionary sources for Define

Research for [Research dictionary datasets, licensing, and private word lookup](https://github.com/A3R0-01/Cloud-Epub/issues/17), supporting [Define in-app dictionary sources and word lookup behavior](https://github.com/A3R0-01/Cloud-Epub/issues/14). Checked 9 October 2026. This is evidence and an engineering recommendation, not a product decision.

## Scope and conclusion

The user has confirmed English words with English definitions, a downloadable offline dictionary, and entirely device-local lookup. Selected words and surrounding passages cannot go to a provider or the Cloud EPUB backend. Thus hosted lookup is outside the confirmed boundary; dataset downloads remain compatible. These boundaries are recorded in [Define in-app dictionary sources and word lookup behavior](https://github.com/A3R0-01/Cloud-Epub/issues/14#issuecomment-6076235569).

**Open English Wordnet 2025 is the strongest compact starting candidate:** maintained upstream, explicit redistribution terms, real glosses/examples, and verified irregular forms. Its limited parts of speech mean predictable misses for function words. **Wiktionary is the stronger broad dictionary source**, including pronouns, contractions, proper names and usage labels, but needs a controlled extraction and pack-validation step. A measured 92 MB English-English distribution demonstrates that the multi-gigabyte raw dump is not the mobile download requirement. Its supplied HTML, sense order and inflection aliases should not be adopted without checking them against Define's behavior.

No Android-ready Cloud EPUB pack was built. No final download/install size, lookup latency, memory budget or coverage percentage for this application has been established.

## Candidates

| Candidate | First-release fit | Main constraint |
|---|---|---|
| Open English Wordnet (OEWN) 2025 core | Compact English definitions; local lookup | Nouns, verbs, adjectives and adverbs; most proper nouns separated into Namenet |
| OEWN 2025+ | Same, with curated proper names | Larger; still not a complete general dictionary |
| Princeton WordNet 3.1 | Local definitions with morphology tables | Upstream no longer developed; prefer maintained OEWN unless compatibility requires it |
| English Wiktionary through Wiktextract/Kaikki | Broad English dictionary; local lookup after packaging | Extraction, licensing provenance, inflection relationships and pack size require validation |
| DanielGregorini/koreader-dicts en-en v1.2.0 | Existing downloadable English dictionary reference | Mixed WordNet 3.0/Wiktionary, pre-rendered HTML, generated aliases and abbreviated notices |
| Simple English Wiktionary extraction | Small alternative with simpler definitions | Much narrower inventory; not interchangeable with full English Wiktionary |
| WikDict | Useful downloadable bilingual data | Its main dictionaries translate language pairs; not an English-English definition source |

OEWN describes its core and plus editions and lists 135,969 core words/107,519 synsets versus 161,875 plus words/120,564 synsets. These are upstream lexical inventory counts, not measured coverage of novels or distinct inflected forms. Its latest published upstream release found was **2025-edition**, released 31 December 2025. [Upstream README](https://github.com/globalwordnet/english-wordnet/blob/bff3181fe5c810dcd157cba0eed60322a6e0aaed/README.md), [release](https://github.com/globalwordnet/english-wordnet/releases/tag/2025-edition).

Princeton identifies WordNet as a lexical database of those four content-word categories and states that its project is no longer developed. Glosses describe senses grouped into synsets; semantic relationships are useful but need not appear in a short Define popup. [Princeton project](https://wordnet.princeton.edu/).

Kaikki's English inventory lists 1,390,507 distinct word forms, with pronouns, articles, contractions and proper names among its categories. The page's snapshot was extracted 3 October 2026 from a 2 September 2026 dump. Its English-only postprocessed JSONL is listed as 3.1 GB and explicitly deprecated. [English inventory](https://kaikki.org/dictionary/English/).

## Download sizes: measured source artifacts, not app estimates

Bytes below are exact local measurements unless explicitly marked published. Decimal MB = 1,000,000 bytes; MiB = 1,048,576 bytes. Unpacked sizes sum file contents, excluding filesystem overhead.

| Source artifact | Download bytes | Unpacked content bytes | Meaning |
|---|---:|---:|---|
| OEWN core WNDB ZIP, current static URL | 9,616,299 (9.62 MB / 9.17 MiB) | 31,889,414 (31.89 MB) | Corrected-index WNDB files; includes four `.exc` files |
| OEWN core WN-LMF XML gzip | 11,363,503 (11.36 MB / 10.84 MiB) | 89,237,271 (89.24 MB) | Structured interchange XML, not a query database |
| OEWN plus WNDB ZIP | 10,982,041 | Not measured | Published GitHub release asset size |
| Princeton 3.1 dict tar.gz | 16,358,468 | 55,076,887 | Original data package, not an Android pack |
| koreader-dicts en-en v1.2.0 ZIP | 91,941,511 (91.94 MB / 87.68 MiB) | 119,077,211 (119.08 MB) | Extracted files retain compressed `.dict.dz` |
| Same StarDict pack with `.dict.dz` expanded | Same download | 341,631,323 (341.63 MB) | Full expansion is optional; reader implementation determines storage |
| Kaikki Simple English JSONL gzip | 4,719,269 (4.72 MB) | 37,900,246 (37.90 MB) | Extraction has 62,156 records/54,056 distinct English spellings |
| Kaikki full English-edition raw JSONL/gzip | Published 23.9 GB / 2.8 GB gzip | Not locally downloaded | Includes hundreds of word languages with English glosses |
| xxyzz StarDict en-en 20260928 tar.zst | Published 136,448,031 | Not measured | Alternative distribution; not inspected or recommended as validated |

Measured URLs: [OEWN WNDB](https://en-word.net/static/english-wordnet-2025.zip), [OEWN XML](https://en-word.net/static/english-wordnet-2025.xml.gz), [Princeton 3.1](https://wordnetcode.princeton.edu/wn3.1.dict.tar.gz), [koreader-dicts v1.2.0](https://github.com/DanielGregorini/koreader-dicts/releases/download/v1.2.0/en-en.zip), [Simple English extraction](https://kaikki.org/dictionary/downloads/simple/simple-extract.jsonl.gz). Published sizes: [OEWN release API](https://api.github.com/repos/globalwordnet/english-wordnet/releases/tags/2025-edition), [Kaikki raw downloads](https://kaikki.org/dictionary/rawdata.html), [xxyzz release API](https://api.github.com/repos/xxyzz/wiktionary_stardict/releases/tags/20260928).

The OEWN downloads page rounds the core WNDB and XML sizes to 9.2 MB and 10.8 MB respectively. GitHub still lists the original core WNDB asset as 9,618,697 bytes and a corrected-index asset as 9,616,299 bytes; the static URL downloaded during this research matched the latter. Pin actual bytes/hash rather than relying on a mutable URL or rounded label. [Downloads](https://en-word.net/downloads), [corrected artifact](https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025-index.sense-fixed.zip).

There is **no defensible numeric Android pack estimate** yet. Keeping only English definitions, examples, source labels and morphology relationships can reduce raw-source size; indexes can add space. A SQLite conversion or different compression changes both download and installed footprint. Source sizes do not bound those outcomes tightly enough to promise a number.

## Morphology and ambiguous meanings

Princeton's Morphy first consults POS-specific exceptions, then applies POS-specific transformations and verifies candidates in the dictionary. It can return multiple base forms, documents limitations around hyphens/collocations, and admits that rules can accept invalid forms. It is a bounded dictionary algorithm, not a linguistic guarantee. [Morphy documentation](https://wordnet.princeton.edu/documentation/morphy7wn).

In the downloaded OEWN core WNDB, `verb.exc` maps **went → go**, `noun.exc` maps **mice → mouse**, and **axes** has separate **ax/axis** exception rows. The XML also stores went/gone and mice as `Form` values attached to go and mouse lexical entries. A WN-LMF import must retain these forms, or a WNDB import must retain exception lists; neither requires arbitrary suffix removal. Both target lemmas are present. These are data inspection results, not a test of a future Android lookup implementation. [WNDB](https://en-word.net/static/english-wordnet-2025.zip), [XML](https://en-word.net/static/english-wordnet-2025.xml.gz).

Wiktextract represents senses, POS, labels, examples, `form_of` and `alt_of`, plus headword form tables. Keep those relationships distinct from ordinary definitions. An English-language Wiktionary edition includes foreign-language entries too: filter `lang_code == en`, rather than assuming every record is an English word. [Extractor/schema documentation](https://github.com/tatuylonen/wiktextract).

Local inspection of the measured OEWN package found bank with **10 noun and 8 verb synsets**, podcast as noun and verb, and add-on as a noun. The pronoun they was absent from its four indexes. No contextual sense selection follows from order; bank's financial and river meanings are distinct. [OEWN source package](https://en-word.net/static/english-wordnet-2025.zip), [bank browser](https://en-word.net/view/lemma/bank).

Wiktionary's mice entry demonstrates a real exact-match collision: besides plural-of-mouse it has an independent regional verb. A policy that stops after any exact entry can hide the ordinary plural interpretation. Preserve the selected spelling, indicate matched base forms, and distinguish meanings; resolving which group appears first remains a product/implementation decision. [Kaikki mice entry](https://kaikki.org/dictionary/English/meaning/m/mi/mice.html).

Selection and lookup should be validated with **went, mice, axes, bank, podcast, they, don't, well-being, London**, and curly-apostrophe variants. Hyphens, apostrophes and proper-name capitalization carry information. Normalization must be deliberate and reversible in displayed text. This list is a proposed acceptance corpus, not a claim that every candidate supports every spelling.

## Inspecting the 92 MB English-English distribution

The [v1.2.0 configuration](https://github.com/DanielGregorini/koreader-dicts/blob/ea66e6746f903fe71ee0a9fe63f8a4dee134e636/configs/pairs/en-en.toml) combines Princeton WordNet 3.0 definitions with English Wiktionary definitions and builds aliases from exception lists, Wiktionary forms and rules. The upstream [inflection implementation](https://github.com/DanielGregorini/koreader-dicts/blob/ea66e6746f903fe71ee0a9fe63f8a4dee134e636/tooling/kdicts/merge/inflect.py) explicitly acknowledges rule overgeneration. Its [build-process document](https://github.com/DanielGregorini/koreader-dicts/blob/ea66e6746f903fe71ee0a9fe63f8a4dee134e636/docs/build-process.md) describes HTML rendering and independent sample lookup verification.

The downloaded ZIP contains `.idx`, `.syn`, `.dict.dz`, `.ifo`, README, LICENSE and ATTRIBUTION. Parsing the index independently found **815,066 entries** and **1,523,450 alias records**; metadata describes **1,494,986 inflected forms**, so those counts should not be equated. The `.ifo` date is 11 September 2026, whereas v1.2.0 was published 21 September. [Immutable release artifact](https://github.com/DanielGregorini/koreader-dicts/releases/download/v1.2.0/en-en.zip).

Observed content and aliases:

| Selected spelling | Exact content observed | Alias targets observed | Implication |
|---|---|---|---|
| went | Obsolete noun senses | gan, go, ween, wend | Exact content does not replace the ordinary inflection |
| mice | Regional verb sense | mouse | Exact-only display loses the ordinary plural |
| axes | No exact entry | ax, axe, axis | Preserve multiple possible lemmas |
| they | Slang noun, verb, ordinary pronoun, determiner | he or she, it, s/he | Pronoun survives, but follows uncommon categories |
| don't | Noun, negative auxiliary verb, interjection | done | Ordinary meaning survives; alias provenance needs audit |
| bank | Separate noun/verb sections and senses | None | Multiple meanings need grouped presentation |
| well-being, London, podcast | Exact entries present | None | Breadth beyond WordNet core |

These results are observations of that artifact; they are **not evidence that Wiktionary lacks ordinary pronoun definitions**. A short popup that displays only the first HTML sense would be misleading for they. Pronunciation is embedded in the HTML despite the confirmed popup excluding it, so the file is not a drop-in display model. Use the structured upstream or an audited adaptation. Example quotations should also be scoped carefully: Wiktionary identifies some external material with separate terms. [Wiktionary copyright policy](https://en.wiktionary.org/wiki/Wiktionary:Copyrights).

Upstream's [source config](https://github.com/DanielGregorini/koreader-dicts/blob/ea66e6746f903fe71ee0a9fe63f8a4dee134e636/configs/sources.toml) pins the Princeton download hash but leaves the Wiktionary source unpinned and points to Kaikki's deprecated English postprocessed gzip. A rebuild of this release is therefore not established as byte-reproducible. The [README](https://github.com/DanielGregorini/koreader-dicts/blob/ea66e6746f903fe71ee0a9fe63f8a4dee134e636/README.md) describes its toolchain as MIT and dictionaries as source-dependent; code and data licenses are separate. Its claimed subtitle top-10k coverage was not rerun and should not be presented as Cloud EPUB or literature coverage.

## License and attribution obligations

**OEWN:** CC BY 4.0 for the community work, with underlying Princeton terms retained. Upstream explicitly requests attribution to both Princeton and the OEWN team. Include original notices/disclaimer, version/source links, license text or link, and an indication of transformations. The WNDB ZIP measured here contains data files but no standalone license file; package notices separately. [OEWN license](https://github.com/globalwordnet/english-wordnet/blob/bff3181fe5c810dcd157cba0eed60322a6e0aaed/LICENSE.md), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

**Princeton:** permits use, modification and distribution without fee, including commercial applications, conditional on retaining its copyright statements and disclaimer on all copies. [Original terms](https://wordnet.princeton.edu/license-and-commercial-use).

**Wiktionary:** entry text is available under CC BY-SA 4.0/GFDL, with identified external content potentially carrying other terms. Under a CC BY-SA route retain attribution/source links, version provenance, license details, changes and ShareAlike for adapted material. The license applies to the data adaptation; this does not by itself establish that the application code must have the same license. The extractor's MIT license does not replace dictionary-content licensing. [Copyright policy](https://en.wiktionary.org/wiki/Wiktionary:Copyrights), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), [Wiktextract license](https://github.com/tatuylonen/wiktextract/blob/master/LICENSE).

**The measured StarDict pack** declares CC BY-SA 4.0, names both sources and links their terms. Its ATTRIBUTION reproduces a shortened Princeton notice but omits the full original warranty disclaimer. Supply complete Princeton notices rather than treating the supplied file as complete. This observation does not establish legal clearance for every embedded quotation. [Measured release](https://github.com/DanielGregorini/koreader-dicts/releases/download/v1.2.0/en-en.zip).

**Other derivatives:** WikDict describes bilingual dictionaries and permits downloads under CC BY-SA; single-language databases provide word metadata rather than an advertised full English definition dictionary. xxyzz's repository identifies GPL-3.0 tooling; its English pack was not inspected for data attribution/provenance, so do not substitute the tool license for Wiktionary's content license. [WikDict about](https://www.wikdict.com/page/about), [downloads](https://www.wikdict.com/page/download), [xxyzz project](https://github.com/xxyzz/wiktionary_stardict).

## Hosted comparison and privacy

| Hosted source | Verified public facts | Decision relevance |
|---|---|---|
| Merriam-Webster | JSON English dictionary products; free non-commercial access up to 1,000 queries/key/day and two references; commercial or higher use requires negotiated fees; logo required | No public fixed commercial price established; no offline redistribution permission inferred |
| Oxford Dictionaries | English datasets; Words accepts inflected forms; Lemmas/Entries flow also documented; paid plans and separately negotiated offline/caching use | Pricing page was inaccessible in this research; no price, SLA or caching entitlement invented |
| dictionaryapi.dev | Public English JSON endpoint; service advertises free access | No verified SLA, quota, current underlying content license or lookup-retention guarantee established |

Sources: [Merriam-Webster FAQ](https://dictionaryapi.com/info/frequently-asked-questions), [products](https://www.dictionaryapi.com/products/index), [terms](https://dictionaryapi.com/info/terms-of-service), [Oxford dictionary endpoints](https://developer.oxforddictionaries.com/dictionary-api), [plans FAQ](https://developer.oxforddictionaries.com/faq), [enterprise offline form](https://developer.oxforddictionaries.com/signup-enterprise), [Free Dictionary API](https://dictionaryapi.dev/).

A hosted lookup sends at least the selected word and usual network connection information to the provider, even if passages and account metadata are excluded. TLS does not hide that word from the receiving service. A proxy would also expose plaintext to the proxy. These are architectural consequences, incompatible with the confirmed local boundary. Provider retention was not verified and is unnecessary to reject hosted lookup here. Dataset downloads reveal a dataset request and connection metadata, not a selected word, if lookup telemetry, external media and lookup-triggered remote calls are absent.

No book content was sent during research: inspected samples were synthetic test strings or public dictionary data. Online uptime claims are not offline functionality, and no candidate received an SLA audit.

## Release validation and maintenance gates

Before provider selection becomes an implementation contract:

1. Choose compact coverage with documented misses, or budget for a broader structured Wiktionary pack. Simple English is another possible small source but needs a deliberate coverage decision.
2. Build the actual pack with only the required fields; preserve POS, labels, separate senses, source attribution, supported irregular forms and form-of relationships. Measure download bytes, installed bytes, peak installation storage, startup memory and lookup latency on Android.
3. Verify exact and base-form collisions, ambiguous lemmas, hyphens, apostrophes, casing and source-order behavior with a larger corpus than the illustrative samples above. Do not silently collapse uncommon lexical senses or claim contextual correctness.
4. Pin source version, extractor commit and every input hash. Emit a pack manifest with schema version, counts, license notices, source revision/date and a content hash. Archive inputs so a deprecated source URL cannot prevent updates or reproducibility.
5. Publish validated packs; verify download integrity and perform atomic activation. Keep the last working pack on interruption or invalid update. Download UI and manual-update policies belong to Define in-app dictionary sources and word lookup behavior, not this report.
6. Audit plaintext lookup telemetry, network-triggered pronunciation/image loading and logs. Lookup itself must operate without a network dependency.

OEWN has dated annual releases and a correction workflow upstream. Kaikki says raw extracts update regularly, usually weekly; refreshing a Cloud EPUB pack should be a reviewed build rather than unconditionally consuming changing upstream URLs. [OEWN releases](https://github.com/globalwordnet/english-wordnet/releases), [Kaikki update policy](https://kaikki.org/dictionary/rawdata.html).

## Reproduction record

The measurements used HTTPS downloads, Python standard-library gzip/tarfile/zipfile/ElementTree, and independent StarDict parsing (NUL-terminated UTF-8 spelling followed by big-endian 32-bit offsets/lengths in `.idx`, indexes in `.syn`). Gzip member content was decompressed only for measurement/inspection. No candidate code was installed or executed; only its public configuration/source was read. Temporary downloaded datasets are not committed.

| Artifact | SHA-256 |
|---|---|
| OEWN core WNDB static URL | `38b16326159f51853626b7d24a44c453fa88ab33f06fce5ec8fc5996d1c2be93` |
| OEWN core XML gzip | `9ca6d1dcb75f822fdd66617f7d9da48142ace38dd544d6ad5e2feca1674ad3fe` |
| Princeton 3.1 tar.gz | `3f7d8be8ef6ecc7167d39b10d66954ec734280b5bdcd57f7d9eafe429d11c22a` |
| koreader-dicts en-en v1.2.0 ZIP | `142327769b0f4e2cd0e442a826bc6e561e5246a7796934ff23618c1c6984ab3c` |
| Simple English extraction gzip | `855cc4c307972c7bda162086135a462977dffc9165a167e54c8d06871317879a` |

Unverified: full Wiktionary extraction correctness, exhaustive source rights for quotations, Android packaging/performance, derivative pack update longevity, API retention/SLA, Oxford numeric pricing, and future source availability. These caveats require validation or product choices; they do not prevent completing the comparative research.

