# Typical corridor design:
# Build parallel tracks with 3 blocks in between nodes,
# use width 9 bridge builder for the outside track(s) and width 5 bridge builder for the inside track(s) (if corridor contains more than 2 tracks),
# and then run the function.

# Example:
# The track bed material is smooth_stone.
# Embankment is made of gravel, has a height of 5m, and a slope of 2H:1V.
embankment smooth_stone gravel 5 0.5
# Tunnel is made of polished andesite and has a height ranging from 7-8m.
tunnel smooth_stone polished_andesite 8
# Fence is made of oak fence and has a height of 3m.
fence "smooth_stone,polished_andesite" oak_fence 3
