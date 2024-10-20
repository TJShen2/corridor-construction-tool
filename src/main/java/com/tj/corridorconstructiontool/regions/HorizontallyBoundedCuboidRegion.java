package com.tj.corridorconstructiontool.regions;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;

/**
 * A subclass of CuboidRegion with convenient methods for finding maximum and minimum x and z in the region.
 */
public class HorizontallyBoundedCuboidRegion extends CuboidRegion {
  public HorizontallyBoundedCuboidRegion(BlockVector3 pos1, BlockVector3 pos2) {
    super(pos1, pos2);
  }
  public HorizontallyBoundedCuboidRegion(CuboidRegion region) {
    this(region.getPos1(), region.getPos2());
  }

  public int getMinimumX() {
      return Math.min(getPos1().getBlockX(), getPos2().getBlockX());
  }

  public int getMaximumX() {
      return Math.max(getPos1().getBlockX(), getPos2().getBlockX());
  }

  public int getMinimumZ() {
    return Math.min(getPos1().getBlockZ(), getPos2().getBlockZ());
  }

  public int getMaximumZ() {
    return Math.max(getPos1().getBlockZ(), getPos2().getBlockZ());
  }
}
