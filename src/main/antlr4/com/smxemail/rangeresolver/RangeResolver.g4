/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

grammar RangeResolver;

resolver: ( repository | imported | resolve | alias | resolved | locked )* EOF;

repository: comment*  REPOSITORY href SEMI;
imported: comment* IMPORT unlocked? resolvedArtifact SEMI;
resolve: comment* RESOLVE bounds rangedArtifact filtering? SEMI;
alias: comment* ALIAS artifact aliasValue SEMI;
resolved: comment* RESOLVED resolvedArtifact FROM resolvedFromArtifact SEMI;
locked: comment* LOCKED resolvedArtifact FROM resolvedFromArtifact SEMI;

comment: COMMENT;
aliasValue: AS value;
href: URL;
bounds: BOUNDS;
unlocked: UNLOCKED;
artifact: group COLON artifactId;
rangedArtifact: group COLON artifactId COLON range;
resolvedArtifact: group COLON artifactId COLON version;
resolvedFromArtifact: group COLON artifactId COLON version;
filtering: FILTERING filter;
filter: REGEX_VALUE;
group: VALUE;
artifactId: VALUE;
version: VALUE;
range: rangeContents;
rangeContents: lowerBound lowerBoundValue COMMA upperBoundValue upperBound;
lowerBound: LOWER_BOUND;
upperBound: UPPER_BOUND;
lowerBoundValue: VALUE;
upperBoundValue: VALUE;
value: VALUE;

REPOSITORY: 'repository';
IMPORT: 'import';
ALIAS: 'alias';
RESOLVE: 'resolve';
FILTERING: 'filtering';
RESOLVED: 'resolved';
LOCKED: 'locked';
UNLOCKED: 'unlocked';
AGAINST: 'against';
AS: 'as';
BOUNDS: ( LOWEST | HIGHEST | SNAPSHOT );
LOWEST: 'lowest';
HIGHEST: 'highest';
SNAPSHOT: 'snapshot';
FROM: 'from';
ON: 'on';


URL: PROTOCOL COLON  ( VALUECHAR | PERIOD | SPACE )+;
PROTOCOL: ('http' | 'https');
VALUE: (VALUECHAR | PERIOD)+;
REGEX_VALUE: '/' (LOWER_BOUND | UPPER_BOUND | REGEXCHAR | VALUECHAR | PERIOD )+ '/';
LOWER_BOUND: [\[(];
UPPER_BOUND: [\])];
REGEXCHAR: [^$*|];
VALUECHAR: [a-zA-Z0-9\\/\-_];
PERIOD: '.';
COLON: ':';
SEMI: ';';
COMMA: ',';

fragment SPACE: ' ';
WS: [ \t\n\r]+ -> skip ; //special TOKEN for skipping whitespaces
COMMENT: '//' ~[\r\n]* '\r'? '\n';
