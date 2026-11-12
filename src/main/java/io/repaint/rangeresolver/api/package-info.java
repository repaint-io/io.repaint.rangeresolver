@Value.Style(
    defaults = @Value.Immutable(intern = true, builder = false),
    typeAbstract = {"Abstract*"},
    typeImmutable = "*",
    visibility = ImplementationVisibility.PUBLIC,
    jdkOnly = true)
package io.repaint.rangeresolver.api;

import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;
