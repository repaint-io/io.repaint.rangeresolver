/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

grammar RangeResolver;

resolver: ( repository | allow | imported | override | resolve | alias | resolved | locked )* EOF;

repository: comment*  REPOSITORY href respositoryGrouping? SEMI;
allow: comment* ALLOW ALLOWABLE REGEX_VALUE SEMI;
imported: comment* IMPORT resolvedArtifact SEMI;
override: comment* OVERRIDE resolution;
resolve: comment* RESOLVE resolution;
resolution: bounds rangedArtifact resolutionGrouping? filtering? SEMI;
alias: comment* ALIAS artifact aliasValue SEMI;
resolved: comment* RESOLVED resolvedArtifact FROM resolvedFromArtifact SEMI;
locked: comment* LOCKED resolvedArtifact FROM resolvedFromArtifact SEMI;

comment: COMMENT;
respositoryGrouping: AS value;
resolutionGrouping: VIA value;
aliasValue: AS value;
href: URL;
bounds: BOUNDS;
artifact: group COLON artifactId;
rangedArtifact: group COLON artifactId COLON range;
classifier: VALUE;
extension: VALUE;
resolvedArtifact: group COLON artifactId COLON version;
resolvedFromArtifact: group COLON artifactId COLON version;
filtering: FILTERING filter;
filter: REGEX_VALUE;
group: VALUE;
artifactId: VALUE;
version: VALUE;
range: fixedRange | boundedRange | implicitRange;
fixedRange: lowerBound value upperBound;
boundedRange: lowerBound lowerBoundValue COMMA upperBoundValue upperBound;
implicitRange: value;
lowerBound: LOWER_BOUND;
upperBound: UPPER_BOUND;
lowerBoundValue: VALUE;
upperBoundValue: VALUE;
value: VALUE;

REPOSITORY: 'repository';
IMPORT: 'import';
ALIAS: 'alias';
OVERRIDE: 'override';
RESOLVE: 'resolve';
ALLOW: 'allow';
ALLOWABLE: ( SNAPSHOTS | UNLOCKED );
FILTERING: 'filtering';
RESOLVED: 'resolved';
LOCKED: 'locked';
UNLOCKED: 'unlocked';
VIA: 'via';
AS: 'as';
BOUNDS: ( LOWEST | HIGHEST | SNAPSHOT );
LOWEST: 'lowest';
HIGHEST: 'highest';
SNAPSHOT: 'snapshot';
SNAPSHOTS: 'snapshots';
FROM: 'from';

URL: PROTOCOL COLON  ( VALUECHAR | PERIOD )+;
PROTOCOL: ('http' | 'https');
VALUE: (VALUECHAR | PERIOD)+;
REGEX_VALUE: SLASH (REGEXCHAR | LOWER_BOUND | UPPER_BOUND | VALUECHAR | PERIOD )+ SLASH;
SLASH: '/';
LOWER_BOUND: [[(];
UPPER_BOUND: [\])];
REGEXCHAR: [^$*\\|];
//VALUECHAR: [a-zA-Z0-9\\/\-_];
VALUECHAR: (('a'..'z') | ('A'..'Z') | ('0'..'9') | '-' | '_' | '/' | '\\');
PERIOD: '.';
COLON: ':';
SEMI: ';';
COMMA: ',';

fragment SPACE: ' ';
WS: [ \t\n\r]+ -> skip ; //special TOKEN for skipping whitespaces
COMMENT: '//' ~[\r\n]* '\r'? '\n';
