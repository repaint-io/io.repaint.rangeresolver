/// The immutable model of a range resolver script and its resolution results. Each `Abstract*` class here is the
/// template for an Immutables-generated value class of the same name without the prefix.
@Value.Style(
        defaults = @Value.Immutable(intern = true, builder = false),
        typeAbstract = {"Abstract*"},
        typeImmutable = "*",
        visibility = ImplementationVisibility.PUBLIC,
        jdkOnly = true)
package io.repaint.rangeresolver.api;

import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;
