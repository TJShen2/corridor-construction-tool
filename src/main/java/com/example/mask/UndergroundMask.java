package com.example.mask;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.mask.AbstractExtentMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import com.sk89q.worldedit.math.BlockVector3;

public class UndergroundMask extends AbstractExtentMask {
  private final EditSession session;
  private final Mask groundMask;
  private final int minDepth;

  public UndergroundMask(EditSession session, Mask groundMask, int minDepth) {
    super(session);
    this.session = session;
    this.groundMask = groundMask;
    this.minDepth = minDepth;
  }

  @Override
  public boolean test(BlockVector3 vector) {
    return session.getHighestTerrainBlock(vector.getX(), vector.getZ(), vector.getY(), 320, groundMask) - vector.getY() >= minDepth;
  }

  @Override
  public Mask2D toMask2D() {
    return null;
  }
}
