# Typical corridor design:
# Build parallel tracks with 3 blocks in between nodes,
# use width 9 bridge builder for the outside track(s) and width 5 bridge builder for the inside track(s) (if corridor contains more than 2 tracks),
# mark the ends,
# and then run the function.

# Example:
# The track bed material is smooth_stone.
# Embankment is made of gravel, has a height of 5m, and a slope of 2H:1V.
elevated 5 0.5 21 50 5 [schematic name] transverse smooth_stone gravel smooth_stone
# Tunnel is made of bricks and has a height ranging from 7-8m.
tunnel 8 false smooth_stone bricks white_concrete
# Fence is made of oak fence and has a height of 3m. Catenary is at a height of 8 and is gantry-mounted.
fence 3 gantry_mounted 8 20 21 "6,14" smooth_stone white_concrete bricks bricks oak_fence
