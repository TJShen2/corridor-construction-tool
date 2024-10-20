package com.tj.corridorconstructiontool.operation;

import java.util.List;

public interface Operation {
  /**
   * Completes the operation.
   * @return whether the operation was completed successfully
   */
  boolean complete();

  /**
   * Converts the operation into an array of SetBlockOperation (the simplest type of operation).
   * @return whether the operation was completed successfully
   */
  List<SetBlockOperation> toSetBlockOperations();
}
