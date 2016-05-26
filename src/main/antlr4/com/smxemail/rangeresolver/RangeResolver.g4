/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

grammar RangeResolver;

resolver: ( repository | imported | resolve | resolved | locked )* EOF;

repository: comment*  REPOSITORY href SEMI;
imported: comment* IMPORT resolvedArtifact SEMI;
resolve: comment* RESOLVE bounds rangedArtifact alias? SEMI;
resolved: comment* RESOLVED resolvedArtifact FROM resolvedFromArtifact SEMI;
locked: comment* LOCKED resolvedArtifact FROM resolvedFromArtifact SEMI;

comment: COMMENT;
alias: AS value;
href: URL;
bounds: BOUNDS;
rangedArtifact: group COLON artifactId COLON range;
resolvedArtifact: group COLON artifactId COLON version;
resolvedFromArtifact: group COLON artifactId COLON version;
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
RESOLVE: 'resolve';
RESOLVED: 'resolved';
LOCKED: 'locked';
AGAINST: 'against';
AS: 'as';
BOUNDS: ( LOWEST | HIGHEST );
LOWEST: 'lowest';
HIGHEST: 'highest';
FROM: 'from';
ON: 'on';

LOWER_BOUND: (LOWER_BOUND_INCLUSIVE | LOWER_BOUND_EXCLUSIVE );
UPPER_BOUND: (UPPER_BOUND_INCLUSIVE | UPPER_BOUND_EXCLUSIVE );
LOWER_BOUND_INCLUSIVE: '[';
LOWER_BOUND_EXCLUSIVE: '(';
UPPER_BOUND_INCLUSIVE: ']';
UPPER_BOUND_EXCLUSIVE: ')';

// URL: QVALUE;
URL: PROTOCOL COLON  ( VALUECHAR | PERIOD | SPACE )+;
PROTOCOL: ('http' | 'https');
//QVALUE: QUOTE VALUECHAR_OR_SPACE+ QUOTE;
fragment QUOTE: '"';
fragment VALUECHAR_OR_SPACE: ( VALUECHAR | SPACE | COLON | PERIOD );
VALUE: (VALUECHAR | PERIOD)+;
VALUECHAR: [a-zA-Z0-9\\/\-_];

PERIOD: '.';
COLON: ':';
SEMI: ';';
COMMA: ',';
fragment SPACE: ' ';
WS: [ \t\n\r]+ -> skip ; //special TOKEN for skipping whitespaces
COMMENT: '//' ~[\r\n]* '\r'? '\n';
