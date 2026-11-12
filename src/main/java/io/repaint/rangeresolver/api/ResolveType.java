package io.repaint.rangeresolver.api;

import dev.zazr.collection.List;

public enum ResolveType {
  IMPORT,
  RESOLVE,
  OVERRIDE,
  DEPRECATE,
  BLACKLIST,
  DOCKER;

  public List<Resolve> from(RangeResolver rangeResolver) {
    return rangeResolver.resolves().filter(res -> res.resolveType() == this);
  }
}
