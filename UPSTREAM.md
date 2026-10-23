# Upstream provenance and compatibility

This project derives its ordering algorithm from:

- Project: https://github.com/rocicorp/fractional-indexing
- Pinned commit: `648d2e60705bb6c7786f5f071d4e3f46deb74067`
- Source: `src/index.js`
- License: CC0-1.0 (full text in `LICENSE`)
- Underlying article: David Greenspan, [Implementing Fractional Indexing](https://observablehq.com/@dgreensp/implementing-fractional-indexing)

`reference/rocicorp/index.mjs` is an unmodified copy of that JavaScript source,
renamed for Node.js module loading. It is used only to generate test fixtures;
it is not packaged in the Java runtime JAR. The Java implementation and the
project's original additions are also provided under CC0-1.0.
This is an independent Java port, not an official Rocicorp release.

## Supported encoding

Only the default encoding is supported: digits `0-9A-Za-z` and integer heads
`A-Za-z`. Custom alphabets and the custom-head feature are intentionally omitted.
For valid, ascending boundaries in this encoding and within the configured limits,
generated keys match the pinned JavaScript reference, with the minimum-integer fix below. Batch output order is
ascending. Existing valid keys do not require migration when using this encoding.
Do not mix keys from other fractional-indexing libraries or encoding options.

## Deliberate differences

1. Reversed or equal boundaries throw `IllegalArgumentException`. The pinned JS
   implementation swaps reversed boundaries in its single-key function; the Java
   API requires ordered boundaries consistently for both methods.
2. Every key character is validated as ASCII Base62; unsupported characters are
   rejected rather than relying on the reference's lookup-table behavior.
3. Boundaries are validated even when `count == 0`; negative counts are rejected.
4. Minimum integer boundary fix: let `M = "A" + 26 zeroes` (a reserved key), and
   `R = "A" + 25 zeroes + "1"`. The reference returns `M` for `(null, R)`, but
   rejects `M` on the next call. Java returns `M + "V"`, a valid key below `R`,
   permitting subsequent prepends. This is tested separately from the reference
   fixtures; it is not a claim of byte-for-byte compatibility at this boundary.
5. Midpoint calculation is iterative and reads the original strings through offsets,
   avoiding both call-stack growth and quadratic suffix-copy allocation on long keys.

6. Java calls default to at most 128 characters per input/output key and 10,000 keys
   per batch. Overloads accept explicit per-call limits (length >= 2, batch >= 1).
   Length violations throw `KeyLengthExceededException`, an `IllegalArgumentException`
   subtype. Count/configuration violations throw `IllegalArgumentException`. Inputs
   are checked before scanning; counts before allocating the result list; fractional
   output growth before appending an over-limit character. No truncation or partial
   batch return occurs. Larger reference cases opt into larger limits in tests.

## Reproducible verification

`node scripts/generate-fixtures.mjs` generates the committed TSV fixtures from
this pinned reference (no npm packages or network required). `mvn clean verify`
compares Java output against all 2,457 vectors and checks invalid input, boundary
transitions, randomized edits, bulk allocation and length growth. Independent review
adds 28,350 reference vectors and constructed-input checks. A thread-allocation
regression test checks single and batch allocation on long keys on supported JVMs
(and skips explicitly when allocation counters are unavailable). Normal Java
builds use the committed fixtures and do not require Node.js.

Review any future upstream change before updating the source and fixtures.
Keep the persisted encoding stable; do not silently adopt changed defaults.
