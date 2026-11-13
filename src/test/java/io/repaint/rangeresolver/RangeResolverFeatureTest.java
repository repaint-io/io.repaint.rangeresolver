package io.repaint.rangeresolver;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("io/repaint/rangeresolver")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "io.repaint.rangeresolver")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/test-report.html, json:target/test-report.json, junit:target/test-report.xml")
public class RangeResolverFeatureTest {}
