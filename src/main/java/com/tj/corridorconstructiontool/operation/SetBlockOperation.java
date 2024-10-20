package com.tj.corridorconstructiontool.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;

/**
 * This record contains the information needed to call the EditSession.setBlock method of the WorldEdit API.
 * @author TJ Shen
 * @version 1.0.0
 */
public record SetBlockOperation(EditSession session, BlockVector3 point, Pattern pattern) implements Operation {
  @Override
  public boolean complete() {
    try {
      session.setBlock(point, pattern);
      return true;
    } catch (MaxChangedBlocksException e) {
      return false;
    }
  }

  @Override
  public List<SetBlockOperation> toSetBlockOperations() {
    return new ArrayList<>(Arrays.asList(this));
  }
}
