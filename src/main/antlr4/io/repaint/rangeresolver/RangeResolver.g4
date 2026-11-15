grammar RangeResolver;

// A script is a sequence of statements. Comments bind to the statement that follows
// them; any comments left over at the end of the file are accepted and discarded.
resolver: ( repository | allow | withhold | imported | override | resolve | deprecate | blacklist | alias | resolved | deprecated | blacklisted | locked )* comment* EOF;

repository: comment*  REPOSITORY href repositoryGrouping? cooldown? SEMI;
allow: comment* ALLOW ALLOWABLE REGEX_VALUE SEMI;
withhold: comment* WITHHOLD REGEX_VALUE SEMI;
imported: comment* IMPORT resolvedArtifact SEMI;
override: comment* OVERRIDE resolution;
resolve: comment* RESOLVE (docker | resolution);
deprecate: comment* DEPRECATE resolution;
blacklist: comment* BLACKLIST resolution;
docker: comment* DOCKER dockerImage SEMI;
dockerImage: dockerImageName COLON version;
// An optional `registry[:port]/` prefix, e.g. `registry.example.com:5000/almalinux:8`.
dockerImageName: identifier (COLON identifier)?;
resolution: bounds rangedArtifact resolutionGrouping? filtering? SEMI;
alias: comment* ALIAS artifact aliasValue SEMI;
resolved: comment* RESOLVED resolvedArtifact SEMI;
locked: comment* LOCKED resolvedArtifact SEMI;
deprecated: comment* DEPRECATED resolvedArtifact SEMI;
blacklisted: comment* BLACKLISTED resolvedArtifact SEMI;

comment: COMMENT;
repositoryGrouping: AS value;
resolutionGrouping: VIA value;
cooldown: COOLDOWN value;
aliasValue: AS value;
href: URL;
bounds: BOUNDS;
artifact: group COLON artifactId;
rangedArtifact: group COLON artifactId COLON range;
resolvedArtifact: (resolvedMavenArtifact | resolvedDockerImage);
resolvedMavenArtifact: group COLON artifactId COLON version;
resolvedDockerImage: artifactId AT digest;
digest: digestAlgorithm COLON digestValue;
digestAlgorithm: identifier;
digestValue: identifier;
filtering: FILTERING filter;
filter: REGEX_VALUE;
group: identifier;
artifactId: identifier;
version: identifier;
range: fixedRange | boundedRange | implicitRange;
fixedRange: lowerBound value upperBound;
// Either bound may be omitted, giving the half-open Maven ranges `[1.0,)` and `(,2.0]`.
boundedRange: lowerBound lowerBoundValue? COMMA upperBoundValue? upperBound;
implicitRange: value;
lowerBound: LOWER_BOUND;
upperBound: UPPER_BOUND;
lowerBoundValue: identifier;
upperBoundValue: identifier;
value: identifier;

// Keywords are contextual: `locked`, `docker`, `snapshots` and friends are perfectly
// legal group ids, artifact ids, versions and grouping names, so every place that
// wants a name accepts a keyword as well as a plain VALUE.
identifier: VALUE | keyword;
keyword:
    REPOSITORY
  | IMPORT
  | ALIAS
  | OVERRIDE
  | DOCKER
  | RESOLVE
  | DEPRECATE
  | BLACKLIST
  | WITHHOLD
  | ALLOW
  | ALLOWABLE
  | FILTERING
  | RESOLVED
  | LOCKED
  | DEPRECATED
  | BLACKLISTED
  | VIA
  | COOLDOWN
  | AS
  | BOUNDS
  ;

REPOSITORY: 'repository';
IMPORT: 'import';
ALIAS: 'alias';
OVERRIDE: 'override';
DOCKER: 'docker';
RESOLVE: 'resolve';
DEPRECATE: 'deprecate';
BLACKLIST: 'blacklist';
WITHHOLD: 'withhold';
ALLOW: 'allow';
ALLOWABLE: SNAPSHOTS | UNLOCKED;
FILTERING: 'filtering';
RESOLVED: 'resolved';
LOCKED: 'locked';
DEPRECATED: 'deprecated';
BLACKLISTED: 'blacklisted';
VIA: 'via';
AS: 'as';
COOLDOWN: 'cooldown';
BOUNDS: ( LOWEST | HIGHEST | SNAPSHOT );

// A comment runs to the end of the line but does not consume the line terminator,
// so a script may end with a comment and no trailing newline.
COMMENT: '//' ~[\r\n]*;

// Any `scheme://` URL: ports, credentials, query strings and `file://` all work.
URL: SCHEME '://' ~[ \t\r\n;]*;

// A slash-delimited regular expression. Everything up to the next unescaped slash is
// part of the pattern, so quantifiers (`+`, `?`, `{2}`) and escapes (`\.`, `\d`) are
// all usable, and a pattern containing no metacharacters at all - `/release/` - is
// still recognised as a regex rather than as a VALUE.
REGEX_VALUE: SLASH ( ~[/\r\n\\] | ESCAPE )+ SLASH;

VALUE: (VALUECHAR | PERIOD )+;

AT: '@';
LOWER_BOUND: [[(];
UPPER_BOUND: [\])];
COLON: ':';
SEMI: ';';
COMMA: ',';

fragment SNAPSHOT: 'snapshot';
fragment SNAPSHOTS: 'snapshots';
fragment UNLOCKED: 'unlocked';
fragment LOWEST: 'lowest';
fragment HIGHEST: 'highest';
fragment SCHEME: [a-zA-Z] [a-zA-Z0-9+.\-]*;
fragment ESCAPE: '\\' .;
fragment SLASH: '/';
fragment VALUECHAR: [a-zA-Z0-9\-_/\\+~];
fragment PERIOD: '.';

WS: [ \t\n\r]+ -> skip ; //special TOKEN for skipping whitespaces
