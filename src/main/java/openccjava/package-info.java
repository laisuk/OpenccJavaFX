/**
 * Chinese script and regional-variant conversion for Java 8 and later.
 *
 * <p>Start with {@link openccjava.OpenCC} and {@link openccjava.OpenccConfig}:
 * configuration names are case-insensitive and surrounding whitespace is ignored.
 * Invalid or null configurations select {@code s2t} and record a diagnostic on
 * the converter. Use {@link openccjava.OpenccConfig#tryParse(String)} to validate
 * a configuration without selecting a fallback.</p>
 *
 * <pre>{@code
 * OpenCC converter = new OpenCC(OpenccConfig.S2T);
 * String traditional = converter.convert("汉字");
 * String withPunctuation = converter.convert("“汉字”", true);
 * }</pre>
 *
 * <p>Configured conversion returns a diagnostic string for null or empty text;
 * it does not return null. Inspect {@link openccjava.OpenCC#getLastError()} for
 * instance diagnostics. Direct directional methods select their own conversion
 * mode independently of the instance configuration.</p>
 *
 * <p>Use {@link openccjava.CustomDictSpec}, {@link openccjava.DictSlot}, and
 * {@link openccjava.CustomDictMode} to customize dictionaries. Constructors
 * accepting custom specifications patch a copy of the shared default dictionary;
 * {@link openccjava.OpenCC#fromDicts(java.util.List)} loads text dictionaries
 * separately. Treat a dictionary as immutable once a converter or conversion
 * cache uses it. Prefer one converter per thread because configuration and
 * diagnostics are mutable.</p>
 *
 * <p>{@link openccjava.OfficeHelper} processes Office, OpenDocument, and EPUB
 * packages using either OpenCC or a caller-supplied {@link openccjava.TextConverter}.
 * {@link openccjava.DeTofu} supplies optional character fallbacks. Compatibility
 * normalization is available through the normalization methods on OpenCC.</p>
 *
 * <p>{@link openccjava.DictRefs}, {@link openccjava.StarterUnion},
 * {@link openccjava.ConversionPlanCache}, and {@link openccjava.UnionKey} expose
 * low-level conversion machinery. Ordinary applications should use OpenCC;
 * cached plans and their backing dictionaries must not be mutated.</p>
 */
package openccjava;
