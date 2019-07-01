package com.shapesecurity.salvation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.shapesecurity.salvation.data.Base64Value;
import com.shapesecurity.salvation.data.Location;
import com.shapesecurity.salvation.data.Notice;
import com.shapesecurity.salvation.data.Origin;
import com.shapesecurity.salvation.data.Policy;
import com.shapesecurity.salvation.data.SchemeHostPortTriple;
import com.shapesecurity.salvation.data.URI;
import com.shapesecurity.salvation.directiveValues.AncestorSource;
import com.shapesecurity.salvation.directiveValues.HashSource;
import com.shapesecurity.salvation.directiveValues.HostSource;
import com.shapesecurity.salvation.directiveValues.KeywordSource;
import com.shapesecurity.salvation.directiveValues.MediaType;
import com.shapesecurity.salvation.directiveValues.NonceSource;
import com.shapesecurity.salvation.directiveValues.None;
import com.shapesecurity.salvation.directiveValues.RFC7230Token;
import com.shapesecurity.salvation.directiveValues.ReportToValue;
import com.shapesecurity.salvation.directiveValues.SchemeSource;
import com.shapesecurity.salvation.directiveValues.SourceExpression;
import com.shapesecurity.salvation.directives.BaseUriDirective;
import com.shapesecurity.salvation.directives.BlockAllMixedContentDirective;
import com.shapesecurity.salvation.directives.ChildSrcDirective;
import com.shapesecurity.salvation.directives.ConnectSrcDirective;
import com.shapesecurity.salvation.directives.DefaultSrcDirective;
import com.shapesecurity.salvation.directives.Directive;
import com.shapesecurity.salvation.directives.DirectiveValue;
import com.shapesecurity.salvation.directives.FontSrcDirective;
import com.shapesecurity.salvation.directives.FormActionDirective;
import com.shapesecurity.salvation.directives.FrameAncestorsDirective;
import com.shapesecurity.salvation.directives.FrameSrcDirective;
import com.shapesecurity.salvation.directives.ImgSrcDirective;
import com.shapesecurity.salvation.directives.ManifestSrcDirective;
import com.shapesecurity.salvation.directives.MediaSrcDirective;
import com.shapesecurity.salvation.directives.NavigateToDirective;
import com.shapesecurity.salvation.directives.ObjectSrcDirective;
import com.shapesecurity.salvation.directives.PluginTypesDirective;
import com.shapesecurity.salvation.directives.PrefetchSrcDirective;
import com.shapesecurity.salvation.directives.ReferrerDirective;
import com.shapesecurity.salvation.directives.ReportToDirective;
import com.shapesecurity.salvation.directives.ReportUriDirective;
import com.shapesecurity.salvation.directives.RequireSriForDirective;
import com.shapesecurity.salvation.directives.SandboxDirective;
import com.shapesecurity.salvation.directives.ScriptSrcAttrDirective;
import com.shapesecurity.salvation.directives.ScriptSrcDirective;
import com.shapesecurity.salvation.directives.ScriptSrcElemDirective;
import com.shapesecurity.salvation.directives.StyleSrcAttrDirective;
import com.shapesecurity.salvation.directives.StyleSrcDirective;
import com.shapesecurity.salvation.directives.StyleSrcElemDirective;
import com.shapesecurity.salvation.directives.UpgradeInsecureRequestsDirective;
import com.shapesecurity.salvation.directives.WorkerSrcDirective;
import com.shapesecurity.salvation.tokens.DirectiveNameToken;
import com.shapesecurity.salvation.tokens.DirectiveSeparatorToken;
import com.shapesecurity.salvation.tokens.DirectiveValueToken;
import com.shapesecurity.salvation.tokens.PolicySeparatorToken;
import com.shapesecurity.salvation.tokens.SubDirectiveValueToken;
import com.shapesecurity.salvation.tokens.Token;
import com.shapesecurity.salvation.tokens.UnknownToken;

public class Parser {

	private static final DirectiveParseException MISSING_DIRECTIVE_NAME =
			new DirectiveParseException("Missing directive-name");
	private static final DirectiveParseException INVALID_DIRECTIVE_NAME =
			new DirectiveParseException("Invalid directive-name");
	private static final DirectiveParseException INVALID_DIRECTIVE_VALUE =
			new DirectiveParseException("Invalid directive-value");
	private static final DirectiveParseException INVALID_MEDIA_TYPE_LIST =
			new DirectiveParseException("Invalid media-type-list");
	private static final DirectiveValueParseException INVALID_MEDIA_TYPE =
			new DirectiveValueParseException("Invalid media-type");
	private static final DirectiveParseException INVALID_SOURCE_LIST =
			new DirectiveParseException("Invalid source-list");
	private static final DirectiveValueParseException INVALID_SOURCE_EXPR =
			new DirectiveValueParseException("Invalid source-expression");
	private static final DirectiveParseException INVALID_ANCESTOR_SOURCE_LIST =
			new DirectiveParseException("Invalid ancestor-source-list");
	private static final DirectiveValueParseException INVALID_ANCESTOR_SOURCE =
			new DirectiveValueParseException("Invalid ancestor-source");
	private static final DirectiveParseException INVALID_REFERRER_TOKEN =
			new DirectiveParseException("Invalid referrer token");
	private static final DirectiveParseException INVALID_REPORT_TO_TOKEN =
			new DirectiveParseException("Invalid report-to token");
	private static final DirectiveParseException INVALID_REQUIRE_SRI_FOR_TOKEN_LIST =
			new DirectiveParseException("Invalid require-sri-for token list");
	private static final DirectiveValueParseException INVALID_REQUIRE_SRI_FOR_TOKEN =
			new DirectiveValueParseException("Invalid require-sri-for token");
	private static final DirectiveParseException INVALID_SANDBOX_TOKEN_LIST =
			new DirectiveParseException("Invalid sandbox token list");
	private static final DirectiveValueParseException INVALID_SANDBOX_TOKEN =
			new DirectiveValueParseException("Invalid sandbox token");
	private static final DirectiveParseException INVALID_URI_REFERENCE_LIST =
			new DirectiveParseException("Invalid uri-reference list");
	private static final DirectiveValueParseException INVALID_URI_REFERENCE =
			new DirectiveValueParseException("Invalid uri-reference");
	private static final DirectiveParseException NON_EMPTY_VALUE_TOKEN_LIST =
			new DirectiveParseException("Non-empty directive-value list");
	private static final String explanation = "Ensure that this pattern is only used for backwards compatibility with older CSP implementations and is not an oversight.";
	private static final String unsafeInlineWarningMessage = "The \"'unsafe-inline'\" keyword-source has no effect in source lists that contain hash-source or nonce-source in CSP2 and later. " + explanation;
	private static final String strictDynamicWarningMessage = "The host-source and scheme-source expressions, as well as the \"'unsafe-inline'\" and \"'self'\" keyword-sources have no effect in source lists that contain \"'strict-dynamic'\" in CSP3 and later. " + explanation;
	private static final String unsafeHashesWithoutHashWarningMessage = "The \"'unsafe-hashes'\" keyword-source has no effect in source lists that do not contain hash-source in CSP3 and later.";

	private enum SeenStates { SEEN_HASH, SEEN_HOST_OR_SCHEME_SOURCE, SEEN_NONE, SEEN_NONCE, SEEN_SELF, SEEN_STRICT_DYNAMIC, SEEN_UNSAFE_EVAL, SEEN_UNSAFE_INLINE, SEEN_UNSAFE_HASHES, SEEN_REPORT_SAMPLE, SEEN_UNSAFE_ALLOW_REDIRECTS }

	@Nonnull
	protected final Token[] tokens;
	@Nonnull
	private final Origin origin;
	protected int index = 0;
	@Nullable
	protected Collection<Notice> noticesOut;

	protected Parser(@Nonnull Token[] tokens, @Nonnull Origin origin, @Nullable Collection<Notice> noticesOut) {
		this.origin = origin;
		this.tokens = tokens;
		this.noticesOut = noticesOut;
	}

	@Nonnull
	public static Policy parse(@Nonnull String sourceText, @Nonnull Origin origin) {
		return new Parser(Tokeniser.tokenise(sourceText), origin, null).parsePolicyAndAssertEOF();
	}

	@Nonnull
	public static Policy parse(@Nonnull String sourceText, @Nonnull String origin) {
		return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), null).parsePolicyAndAssertEOF();
	}

	@Nonnull
	public static Policy parse(@Nonnull String sourceText, @Nonnull Origin origin,
		@Nonnull Collection<Notice> warningsOut) {
		return new Parser(Tokeniser.tokenise(sourceText), origin, warningsOut).parsePolicyAndAssertEOF();
	}

	@Nonnull
	public static Policy parse(@Nonnull String sourceText, @Nonnull String origin,
		@Nonnull Collection<Notice> warningsOut) {
		return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), warningsOut).parsePolicyAndAssertEOF();
	}

	@Nonnull
	public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull Origin origin) {
		return new Parser(Tokeniser.tokenise(sourceText), origin, null).parsePolicyListAndAssertEOF();
	}

	@Nonnull
	public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull String origin) {
		return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), null).parsePolicyListAndAssertEOF();
	}

	@Nonnull
	public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull Origin origin,
		@Nonnull Collection<Notice> warningsOut) {
		return new Parser(Tokeniser.tokenise(sourceText), origin, warningsOut).parsePolicyListAndAssertEOF();
	}

	@Nonnull
	public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull String origin,
		@Nonnull Collection<Notice> warningsOut) {
		return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), warningsOut).parsePolicyListAndAssertEOF();
	}

	@Nonnull
	protected Notice createNotice(@Nonnull Notice.Type type, @Nonnull String message) {
		return new Notice(type, message);
	}

	@Nonnull
	protected Notice createNotice(@Nullable Token token, @Nonnull Notice.Type type, @Nonnull String message) {
		return new Notice(type, message);
	}

	private void warn(@Nullable Token token, @Nonnull String message) {
		if (this.noticesOut != null) {
			this.noticesOut.add(this.createNotice(token, Notice.Type.WARNING, message));
		}
	}

	private void error(@Nullable Token token, @Nonnull String message) {
		if (this.noticesOut != null) {
			this.noticesOut.add(this.createNotice(token, Notice.Type.ERROR, message));
		}
	}

	private void info(@Nullable Token token, @Nonnull String message) {
		if (this.noticesOut != null) {
			this.noticesOut.add(this.createNotice(token, Notice.Type.INFO, message));
		}
	}

	@Nonnull
	private Token advance() {
		return this.tokens[this.index++];
	}

	protected boolean hasNext() {
		return this.index < this.tokens.length;
	}

	private boolean hasNext(@Nonnull Class<? extends Token> c) {
		return this.hasNext() && c.isAssignableFrom(this.tokens[this.index].getClass());
	}

	private boolean eat(@Nonnull Class<? extends Token> c) {
		if (this.hasNext(c)) {
			this.advance();
			return true;
		}
		return false;
	}

	@Nonnull
	protected Policy parsePolicy() {
		Policy policy = new Policy(this.origin);
		LinkedHashMap<Class<? extends Directive>, Directive<? extends DirectiveValue>> directives = new LinkedHashMap<>();
		while (this.hasNext()) {
			if (this.hasNext(PolicySeparatorToken.class)) {
				break;
			}
			if (this.eat(DirectiveSeparatorToken.class)) {
				continue;
			}
			try {
				Directive<? extends DirectiveValue> directive = this.parseDirective();
				// only add a directive if it doesn't exist; used for handling duplicate directives in CSP headers
				if (!directives.containsKey(directive.getClass())) {
					directives.put(directive.getClass(), directive);
				} else {
					this.warn(this.tokens[this.index - 2], "Policy contains more than one " + directive.name + " directive. All but the first instance will be ignored.");
				}
			} catch (DirectiveParseException ignored) {
			}
		}
		policy.addDirectives(directives.values());
		return policy;
	}

	@Nonnull
	protected Policy parsePolicyAndAssertEOF() {
		Policy policy = this.parsePolicy();
		if (this.hasNext()) {
			Token t = this.advance();
			this.error(t, "Expecting end of policy but found \"" + t.value + "\".");
		}
		return policy;
	}

	@Nonnull
	protected List<Policy> parsePolicyList() {
		List<Policy> policies = new ArrayList<>();
		policies.add(this.parsePolicy());
		while (this.hasNext(PolicySeparatorToken.class)) {
			while (this.eat(PolicySeparatorToken.class))
				;
			policies.add(this.parsePolicy());
		}
		return policies;
	}

	@Nonnull
	protected List<Policy> parsePolicyListAndAssertEOF() {
		List<Policy> policies = this.parsePolicyList();
		if (this.hasNext()) {
			Token t = this.advance();
			this.error(t, "Expecting end of policy list but found \"" + t.value + "\".");
		}
		return policies;
	}

	@Nonnull
	private Directive<?> parseDirective() throws DirectiveParseException {
		if (!this.hasNext(DirectiveNameToken.class)) {
			Token t = this.advance();
			this.error(t, "Expecting directive-name but found \"" + t.value.split(" ", 2)[0] + "\".");
			throw MISSING_DIRECTIVE_NAME;
		}
		Directive result;
		DirectiveNameToken token = (DirectiveNameToken) this.advance();
		try {
			switch (token.subtype) {
				case BaseUri:
					result = new BaseUriDirective(this.parseSourceList());
					break;
				case BlockAllMixedContent:
					warnFutureDirective(token);
					this.enforceMissingDirectiveValue(token);
					result = new BlockAllMixedContentDirective();
					break;
				case ChildSrc:
					this.warn(token,
							"The child-src directive is deprecated as of CSP level 3. Authors who wish to regulate nested browsing contexts and workers SHOULD use the frame-src and worker-src directives, respectively.");
					result = new ChildSrcDirective(this.parseSourceList());
					break;
				case ConnectSrc:
					result = new ConnectSrcDirective(this.parseSourceList());
					break;
				case DefaultSrc:
					result = new DefaultSrcDirective(this.parseSourceList());
					break;
				case FontSrc:
					result = new FontSrcDirective(this.parseSourceList());
					break;
				case FormAction:
					result = new FormActionDirective(this.parseSourceList());
					break;
				case FrameAncestors:
					result = new FrameAncestorsDirective(this.parseAncestorSourceList());
					break;
				case ImgSrc:
					result = new ImgSrcDirective(this.parseSourceList());
					break;
				case ManifestSrc:
					warnFutureDirective(token);
					result = new ManifestSrcDirective(this.parseSourceList());
					break;
				case MediaSrc:
					result = new MediaSrcDirective(this.parseSourceList());
					break;
				case NavigateTo:
					result = new NavigateToDirective(this.parseSourceList());
					break;
				case ObjectSrc:
					result = new ObjectSrcDirective(this.parseSourceList());
					break;
				case PluginTypes:
					Set<MediaType> mediaTypes = this.parseMediaTypeList();
					if (mediaTypes.isEmpty()) {
						this.error(token, "The media-type-list must contain at least one media-type.");
						throw INVALID_MEDIA_TYPE_LIST;
					} else if (mediaTypes.stream().anyMatch(x -> x.type.equals("*") || x.subtype.equals("*"))) {
						this.warn(token, "Media types can only be matched literally. Make sure using `*` is not an oversight.");
					}
					result = new PluginTypesDirective(mediaTypes);
					break;
				case PrefetchSrc:
					result = new PrefetchSrcDirective(this.parseSourceList());
					break;
				case Referrer:
					this.warn(token,
							"The referrer directive was an experimental directive that was proposed but never added to the CSP specification. Support for this directive will be removed. See Referrer Policy specification.");
					result = new ReferrerDirective(this.parseReferrerToken(token));
					break;
				case ReportTo:
					result = new ReportToDirective(this.parseReportToToken(token));
					break;
				case ReportUri:
					// TODO: bump to .warn once CSP3 becomes RC
					this.info(token,
							"A draft of the next version of CSP deprecates report-uri in favour of a new report-to directive.");
					Set<URI> uriList = this.parseUriList();
					if (uriList.isEmpty()) {
						this.error(token, "The report-uri directive must contain at least one uri-reference.");
						throw INVALID_URI_REFERENCE_LIST;
					}
					result = new ReportUriDirective(uriList);
					break;
				case RequireSriFor:
					result = new RequireSriForDirective(this.parseRequireSriForTokenList(token));
					break;
				case Sandbox:
					result = new SandboxDirective(this.parseSandboxTokenList());
					break;
				case ScriptSrc:
					result = new ScriptSrcDirective(this.parseSourceList());
					break;
				case ScriptSrcElem:
					result = new ScriptSrcElemDirective(this.parseSourceList());
					break;
				case ScriptSrcAttr:
					result = new ScriptSrcAttrDirective(this.parseSourceList());
					break;
				case StyleSrc:
					result = new StyleSrcDirective(this.parseSourceList());
					break;
				case StyleSrcElem:
					result = new StyleSrcElemDirective(this.parseSourceList());
					break;
				case StyleSrcAttr:
					result = new StyleSrcAttrDirective(this.parseSourceList());
					break;
				case UpgradeInsecureRequests:
					warnFutureDirective(token);
					this.enforceMissingDirectiveValue(token);
					result = new UpgradeInsecureRequestsDirective();
					break;
				case WorkerSrc:
					result = new WorkerSrcDirective(this.parseSourceList());
					break;
				case Allow:
					this.error(token,
							"The allow directive has been replaced with default-src and is not in the CSP specification.");
					this.eat(DirectiveValueToken.class);
					throw INVALID_DIRECTIVE_NAME;
				case FrameSrc:
					result = new FrameSrcDirective(this.parseSourceList());
					break;
				case Options:
					this.error(token,
							"The options directive has been replaced with 'unsafe-inline' and 'unsafe-eval' and is not in the CSP specification.");
					this.eat(DirectiveValueToken.class);
					throw INVALID_DIRECTIVE_NAME;
				case Unrecognised:
				default:
					this.error(token, "Unrecognised directive-name: \"" + token.value + "\".");
					this.eat(DirectiveValueToken.class);
					throw INVALID_DIRECTIVE_NAME;
			}
		} finally {
			if (this.hasNext(UnknownToken.class)) {
				Token t = this.advance();
				int cp = t.value.codePointAt(0);
				this.error(t, String.format(
						"Expecting directive-value but found U+%04X (%s). Non-ASCII and non-printable characters must be percent-encoded.",
						cp, new String(new int[]{cp}, 0, 1)));
				throw INVALID_DIRECTIVE_VALUE;
			}
		}
		return result;
	}

	private void warnFutureDirective(DirectiveNameToken token) {
		this.warn(token, "The " + token.value
				+ " directive is an experimental directive that will be likely added to the CSP specification.");
	}

	private void enforceMissingDirectiveValue(@Nonnull Token directiveNameToken) throws DirectiveParseException {
		if (this.eat(DirectiveValueToken.class)) {
			this.error(directiveNameToken, "The " + directiveNameToken.value + " directive must not contain any value.");
			throw NON_EMPTY_VALUE_TOKEN_LIST;
		}
	}

	@Nonnull
	private Set<MediaType> parseMediaTypeList() throws DirectiveParseException {
		Set<MediaType> mediaTypes = new LinkedHashSet<>();
		boolean parseException = false;
		while (this.hasNext(SubDirectiveValueToken.class)) {
			try {
				mediaTypes.add(this.parseMediaType());
			} catch (DirectiveValueParseException e) {
				parseException = true;
			}
		}
		if (parseException) {
			throw INVALID_MEDIA_TYPE_LIST;
		}
		return mediaTypes;
	}

	@Nonnull
	private MediaType parseMediaType() throws DirectiveValueParseException {
		Token token = this.advance();
		Matcher matcher = Constants.mediaTypePattern.matcher(token.value);
		if (matcher.find()) {
			return new MediaType(matcher.group("type"), matcher.group("subtype"));
		}
		this.error(token, "Expecting media-type but found \"" + token.value + "\".");
		throw INVALID_MEDIA_TYPE;
	}

	@Nonnull
	private Set<SourceExpression> parseSourceList() throws DirectiveParseException {
		Set<SourceExpression> sourceExpressions = new LinkedHashSet<>();
		boolean parseException = false;
		Set<SeenStates> seenStates = new HashSet<>();
		while (this.hasNext(SubDirectiveValueToken.class)) {
			try {
				SourceExpression se = this.parseSourceExpression(seenStates, !sourceExpressions.isEmpty());
				if (se == None.INSTANCE) {
					seenStates.add(SeenStates.SEEN_NONE);
				} else if (se == KeywordSource.UnsafeEval) {
					seenStates.add(SeenStates.SEEN_UNSAFE_EVAL);
				} else if (se == KeywordSource.Self) {
					seenStates.add(SeenStates.SEEN_SELF);
				} else if (se == KeywordSource.UnsafeInline) {
					seenStates.add(SeenStates.SEEN_UNSAFE_INLINE);
				} else if (se instanceof HashSource) {
					seenStates.add(SeenStates.SEEN_HASH);
				} else if (se instanceof NonceSource) {
					seenStates.add(SeenStates.SEEN_NONCE);
				} else if (se == KeywordSource.StrictDynamic) {
					seenStates.add(SeenStates.SEEN_STRICT_DYNAMIC);
				} else if (se instanceof HostSource || se instanceof SchemeSource) {
					seenStates.add(SeenStates.SEEN_HOST_OR_SCHEME_SOURCE);
				} else if (se == KeywordSource.UnsafeHashes) {
					seenStates.add(SeenStates.SEEN_UNSAFE_HASHES);
				} else if (se == KeywordSource.ReportSample) {
					seenStates.add(SeenStates.SEEN_REPORT_SAMPLE);
				} else if (se == KeywordSource.UnsafeAllowRedirects) {
					seenStates.add(SeenStates.SEEN_UNSAFE_ALLOW_REDIRECTS);
				}
				if (!sourceExpressions.add(se)) {
					this.warn(this.tokens[this.index - 1], "Source list contains duplicate source expression \"" + se.show() + "\". All but the first instance will be ignored.");
				}
			} catch (DirectiveValueParseException e) {
				parseException = true;
			}
		}
		if (seenStates.contains(SeenStates.SEEN_UNSAFE_HASHES) && !seenStates.contains(SeenStates.SEEN_HASH)) {
			this.warn(this.tokens[0], unsafeHashesWithoutHashWarningMessage);
		}
		if (parseException) {
			throw INVALID_SOURCE_LIST;
		}
		return sourceExpressions;
	}

	@Nonnull
	private SourceExpression parseSourceExpression(Set<SeenStates> seenStates, boolean seenSome)
			throws DirectiveValueParseException {
		Token token = this.advance();
		if (seenStates.contains(SeenStates.SEEN_NONE) || seenSome && token.value.equalsIgnoreCase("'none'")) {
			this.error(token, "'none' must not be combined with any other source-expression.");
			throw INVALID_SOURCE_EXPR;
		}
		switch (token.value.toLowerCase()) {
			case "'none'":
				return None.INSTANCE;
			case "'self'":
				if (seenStates.contains(SeenStates.SEEN_STRICT_DYNAMIC)) {
					this.info(token, strictDynamicWarningMessage);
				}
				return KeywordSource.Self;
			case "'strict-dynamic'":
				if (seenStates.contains(SeenStates.SEEN_UNSAFE_INLINE) || seenStates.contains(SeenStates.SEEN_HOST_OR_SCHEME_SOURCE) || seenStates.contains(SeenStates.SEEN_SELF)) {
					this.info(token, strictDynamicWarningMessage);
				}
				return KeywordSource.StrictDynamic;
			case "'unsafe-inline'":
				if (seenStates.contains(SeenStates.SEEN_HASH) || seenStates.contains(SeenStates.SEEN_NONCE)) {
					this.info(token, unsafeInlineWarningMessage);
				}
				if (seenStates.contains(SeenStates.SEEN_STRICT_DYNAMIC)) {
					this.info(token, strictDynamicWarningMessage);
				}
				return KeywordSource.UnsafeInline;
			case "'unsafe-eval'":
				return KeywordSource.UnsafeEval;
			case "'unsafe-redirect'":
				this.warn(token, "'unsafe-redirect' has been removed from CSP as of version 2.0.");
				return KeywordSource.UnsafeRedirect;
			case "'unsafe-hashes'":
				return KeywordSource.UnsafeHashes;
			case "'report-sample'":
				return KeywordSource.ReportSample;
			case "'unsafe-allow-redirects'":
				return KeywordSource.UnsafeAllowRedirects;
			default:
				checkForUnquotedKeyword(token);
				if (token.value.startsWith("'nonce-")) {
					String nonce = token.value.substring(7, token.value.length() - 1);
					NonceSource nonceSource = new NonceSource(nonce);
					nonceSource.validationErrors().forEach(str -> this.warn(token, str));
					if (seenStates.contains(SeenStates.SEEN_UNSAFE_INLINE)) {
						this.info(token, unsafeInlineWarningMessage);
					}
					return nonceSource;
				} else if (token.value.toLowerCase().startsWith("'sha")) {
					HashSource.HashAlgorithm algorithm;
					switch (token.value.substring(4, 7)) {
						case "256":
							algorithm = HashSource.HashAlgorithm.SHA256;
							break;
						case "384":
							algorithm = HashSource.HashAlgorithm.SHA384;
							break;
						case "512":
							algorithm = HashSource.HashAlgorithm.SHA512;
							break;
						default:
							this.error(token, "Unrecognised hash algorithm: \"" + token.value.substring(1, 7) + "\".");
							throw INVALID_SOURCE_EXPR;
					}
					String value = token.value.substring(8, token.value.length() - 1);
					// convert url-safe base64 to RFC4648 base64
					String safeValue = value.replace('-', '+').replace('_', '/');
					Base64Value base64Value;
					try {
						base64Value = new Base64Value(safeValue);
					} catch (IllegalArgumentException e) {
						this.error(token, e.getMessage());
						throw INVALID_SOURCE_EXPR;
					}
					// warn if value is not RFC4648
					if (value.contains("-") || value.contains("_")) {
						this.warn(token,
								"Invalid base64-value (characters are not in the base64-value grammar). Consider using RFC4648 compliant base64 encoding implementation.");
					}
					HashSource hashSource = new HashSource(algorithm, base64Value);
					try {
						hashSource.validationErrors();
					} catch (IllegalArgumentException e) {
						this.error(token, e.getMessage());
						throw INVALID_SOURCE_EXPR;
					}
					if (seenStates.contains(SeenStates.SEEN_UNSAFE_INLINE)) {
						this.info(token, unsafeInlineWarningMessage);
					}
					return hashSource;
				} else if (token.value.matches("^" + Constants.schemePart + ":$")) {
					if (seenStates.contains(SeenStates.SEEN_STRICT_DYNAMIC)) {
						this.info(token, strictDynamicWarningMessage);
					}
					return new SchemeSource(token.value.substring(0, token.value.length() - 1));
				} else if (token.value.equalsIgnoreCase("'unsafe-hashed-attributes'")) {
					this.warn(token, "The CSP specification renamed 'unsafe-hashed-attributes' to 'unsafe-hashes' (June 2018).");
				} else {
					Matcher matcher = Constants.hostSourcePattern.matcher(token.value);
					if (matcher.find()) {
						String scheme = matcher.group("scheme");
						if (scheme != null) {
							scheme = scheme.substring(0, scheme.length() - 3);
						}
						String portString = matcher.group("port");
						int port;
						if (portString == null) {
							port = scheme == null ? Constants.EMPTY_PORT : SchemeHostPortTriple.defaultPortForProtocol(scheme);
						} else {
							port = portString.equals(":*") ? Constants.WILDCARD_PORT : Integer.parseInt(portString.substring(1));
						}
						if (seenStates.contains(SeenStates.SEEN_STRICT_DYNAMIC)) {
							this.info(token, strictDynamicWarningMessage);
						}
						String host = matcher.group("host");
						String path = matcher.group("path");
						return new HostSource(scheme, host, port, path);
					}
				}
		}
		this.error(token, "Expecting source-expression but found \"" + token.value + "\".");
		throw INVALID_SOURCE_EXPR;
	}

	@Nonnull
	private Set<AncestorSource> parseAncestorSourceList() throws DirectiveParseException {
		Set<AncestorSource> ancestorSources = new LinkedHashSet<>();
		boolean parseException = false;
		boolean seenNone = false;
		while (this.hasNext(SubDirectiveValueToken.class)) {
			try {
				AncestorSource ancestorSource = this.parseAncestorSource(seenNone, !ancestorSources.isEmpty());
				if (ancestorSource == None.INSTANCE) {
					seenNone = true;
				}
				ancestorSources.add(ancestorSource);
			} catch (DirectiveValueParseException e) {
				parseException = true;
			}
		}
		if (parseException) {
			throw INVALID_ANCESTOR_SOURCE_LIST;
		}
		return ancestorSources;
	}

	@Nonnull
	private AncestorSource parseAncestorSource(boolean seenNone, boolean seenSome)
			throws DirectiveValueParseException {
		Token token = this.advance();
		if (seenNone || seenSome && token.value.equalsIgnoreCase("'none'")) {
			this.error(token, "'none' must not be combined with any other ancestor-source.");
			throw INVALID_ANCESTOR_SOURCE;
		}
		if (token.value.equalsIgnoreCase("'none'")) {
			return None.INSTANCE;
		}
		if (token.value.equalsIgnoreCase("'self'")) {
			return KeywordSource.Self;
		}
		checkForUnquotedKeyword(token);
		if (token.value.matches("^" + Constants.schemePart + ":$")) {
			return new SchemeSource(token.value.substring(0, token.value.length() - 1));
		} else {
			Matcher matcher = Constants.hostSourcePattern.matcher(token.value);
			if (matcher.find()) {
				String scheme = matcher.group("scheme");
				if (scheme != null) {
					scheme = scheme.substring(0, scheme.length() - 3);
				}
				String portString = matcher.group("port");
				int port;
				if (portString == null) {
					port = scheme == null ? Constants.EMPTY_PORT : SchemeHostPortTriple.defaultPortForProtocol(scheme);
				} else {
					port = portString.equals(":*") ? Constants.WILDCARD_PORT : Integer.parseInt(portString.substring(1));
				}
				String host = matcher.group("host");
				String path = matcher.group("path");
				return new HostSource(scheme, host, port, path);
			}
		}
		this.error(token, "Expecting ancestor-source but found \"" + token.value + "\".");
		throw INVALID_ANCESTOR_SOURCE;
	}

	private void checkForUnquotedKeyword(@Nonnull Token token) {
		if (Constants.unquotedKeywordPattern.matcher(token.value).find()) {
			this.warn(token,
					"This host name is unusual, and likely meant to be a keyword that is missing the required quotes: \'"
							+ token.value + "\'.");
		}
	}

	@Nonnull
	private RFC7230Token parseReferrerToken(@Nonnull Token directiveNameToken) throws DirectiveParseException {
		if (this.hasNext(DirectiveValueToken.class)) {
			Token token = this.advance();
			Matcher matcher = Constants.referrerTokenPattern.matcher(Tokeniser.trimRHSWS(token.value));
			if (matcher.find()) {
				return new RFC7230Token(token.value);
			}
			this.error(token, "Expecting referrer directive value but found \"" + token.value + "\".");
		} else {
			this.error(directiveNameToken, "The referrer directive must contain exactly one referrer directive value.");
			throw INVALID_DIRECTIVE_VALUE;
		}
		throw INVALID_REFERRER_TOKEN;
	}

	@Nonnull
	private ReportToValue parseReportToToken(@Nonnull Token directiveNameToken) throws DirectiveParseException {
		if (this.hasNext(DirectiveValueToken.class)) {
			Token token = this.advance();
			Matcher matcher = Constants.rfc7230TokenPattern.matcher(Tokeniser.trimRHSWS(token.value));
			if (matcher.find()) {
				return new ReportToValue(token.value);
			}
			this.error(token, "Expecting RFC 7230 token but found \"" + token.value + "\".");
		} else {
			this.error(directiveNameToken, "The report-to directive must contain exactly one RFC 7230 token.");
		}
		throw INVALID_REPORT_TO_TOKEN;
	}

	@Nonnull
	private Set<RFC7230Token> parseRequireSriForTokenList(@Nonnull Token directiveNameToken) throws DirectiveParseException {
		Set<RFC7230Token> requireSriForTokens = new LinkedHashSet<>();
		boolean parseException = false;
		while (this.hasNext(SubDirectiveValueToken.class)) {
			try {
				RFC7230Token rsfToken = this.parseRequireSriForToken();
				if (!requireSriForTokens.add(rsfToken)) {
					this.warn(directiveNameToken, "The require-sri-for directive contains duplicate token: \"" + rsfToken.show() + "\".");
				}
			} catch (DirectiveValueParseException e) {
				parseException = true;
			}
		}
		if (parseException) {
			throw INVALID_REQUIRE_SRI_FOR_TOKEN_LIST;
		}
		if (requireSriForTokens.isEmpty()) {
			this.warn(directiveNameToken, "Empty require-sri-for directive has no effect.");
		}
		return requireSriForTokens;
	}

	@Nonnull
	private RFC7230Token parseRequireSriForToken() throws DirectiveValueParseException {
		Token token = this.advance();
		Matcher matcher = Constants.requireSriForEnumeratedTokenPattern.matcher(token.value);
		if (matcher.find()) {
			return new RFC7230Token(token.value.toLowerCase());
		} else {
			this.warn(token, "The require-sri-for directive should contain only \"script\", \"style\" tokens.");
			matcher = Constants.rfc7230TokenPattern.matcher(token.value);
			if (matcher.find()) {
				return new RFC7230Token(token.value);
			}
		}

		this.error(token, "Expecting RFC 7230 token but found \"" + token.value + "\".");
		throw INVALID_REQUIRE_SRI_FOR_TOKEN;
	}

	@Nonnull
	private Set<RFC7230Token> parseSandboxTokenList() throws DirectiveParseException {
		Set<RFC7230Token> sandboxTokens = new LinkedHashSet<>();
		boolean parseException = false;
		while (this.hasNext(SubDirectiveValueToken.class)) {
			try {
				sandboxTokens.add(this.parseSandboxToken());
			} catch (DirectiveValueParseException e) {
				parseException = true;
			}
		}
		if (parseException) {
			throw INVALID_SANDBOX_TOKEN_LIST;
		}
		return sandboxTokens;
	}

	@Nonnull
	private RFC7230Token parseSandboxToken() throws DirectiveValueParseException {
		Token token = this.advance();
		Matcher matcher = Constants.sandboxEnumeratedTokenPattern.matcher(token.value);
		if (matcher.find()) {
			return new RFC7230Token(token.value);
		} else {
			this.warn(token, "The sandbox directive should contain only allow-forms, allow-modals, "
					+ "allow-pointer-lock, allow-popups, allow-popups-to-escape-sandbox, "
					+ "allow-same-origin, allow-scripts, or allow-top-navigation.");
			matcher = Constants.rfc7230TokenPattern.matcher(token.value);
			if (matcher.find()) {
				return new RFC7230Token(token.value);
			}
		}

		this.error(token, "Expecting RFC 7230 token but found \"" + token.value + "\".");
		throw INVALID_SANDBOX_TOKEN;
	}

	@Nonnull
	private Set<URI> parseUriList() throws DirectiveParseException {
		Set<URI> uriList = new LinkedHashSet<>();
		boolean parseException = false;
		while (this.hasNext(SubDirectiveValueToken.class)) {
			try {
				uriList.add(this.parseUri());
			} catch (DirectiveValueParseException e) {
				parseException = true;
			}
		}
		if (parseException) {
			throw INVALID_URI_REFERENCE_LIST;
		}
		return uriList;
	}

	@Nonnull
	private URI parseUri() throws DirectiveValueParseException {
		Token token = this.advance();
		try {
			return URI.parseWithOrigin(this.origin, token.value);
		} catch (IllegalArgumentException ignored) {
			this.error(token, "Expecting uri-reference but found \"" + token.value + "\".");
			throw INVALID_URI_REFERENCE;
		}
	}

	private static class DirectiveParseException extends Exception {
		@Nullable
		Location startLocation;
		@Nullable
		Location endLocation;

		private DirectiveParseException(@Nonnull String message) {
			super(message);
		}

		@Nonnull
		@Override
		public String getMessage() {
			if (startLocation == null) {
				return super.getMessage();
			}
			return startLocation.show() + ": " + super.getMessage();
		}
	}


	protected static class DirectiveValueParseException extends Exception {
		@Nullable
		Location startLocation;
		@Nullable
		Location endLocation;

		private DirectiveValueParseException(@Nonnull String message) {
			super(message);
		}

		@Nonnull
		@Override
		public String getMessage() {
			if (startLocation == null) {
				return super.getMessage();
			}
			return startLocation.show() + ": " + super.getMessage();
		}
	}
}
