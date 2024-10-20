package com.tj.corridorconstructiontool.function.mask;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.mask.AbstractExtentMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import com.sk89q.worldedit.math.BlockVector3;

/**
 * A mask that represents blocks that are a certain distance or more below the highest terrain block at a particular {@code BlockVector2}.
 * @author TJ Shen
 * @version 1.0.0
 */
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
    return session.getHighestTerrainBlock(vector.getX(), vector.getZ(), vector.getY(), session.getWorld().getMaxY(), groundMask) - vector.getY() >= minDepth;
  }

  @Override
  public Mask2D toMask2D() {
    return null;
  }
}
