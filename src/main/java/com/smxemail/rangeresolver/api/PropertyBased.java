package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

/**
 * Created by amrk on 31/05/16.
 */
public interface PropertyBased {
  @Value.Derived
  String key();

  String propertyName();
}
