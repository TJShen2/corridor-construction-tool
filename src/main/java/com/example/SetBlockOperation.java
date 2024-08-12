package com.example;

import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;

public record SetBlockOperation(BlockVector3 point, Pattern pattern) {}
