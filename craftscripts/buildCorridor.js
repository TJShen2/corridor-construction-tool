//pos1 -7378,65,-5599
//pos2 -7704,71,-5552

//Current process
//Build tracks with 4 blocks in between nodes
//Use width 9 bridge builder
//Script will build 9-10 block high tunnel
/cs createEmbankment3.js smooth_stone ##transit_corridor:railway_embankment_replaceable gravel 5 0.5
/cs createTunnelCutting2.js smooth_stone polished_andesite ##transit_corridor:railway_embankment_replaceable 8 30
/cs createFencing.js smooth_stone,polished_andesite ##transit_corridor:railway_embankment_replaceable oak_fence 3
//todo: create bridge pillars
//todo: create overhead line (requires integration with fencing design)

//Deprecated scripts
/cs createTunnelCutting1.js smooth_stone smooth_stone ##transit_corridor:railway_embankment_replaceable 5 oak_fence 2
/cs createEmbankment1.js smooth_stone ##transit_corridor:railway_embankment_replaceable
/cs createEmbankment2.js smooth_stone ##transit_corridor:railway_embankment_replaceable black_concrete 10 0.5
//A historical run of createEmbankment2 acted on a 327 m long section of track, changed 17848 blocks, and took 32355 ticks or 1617.75 seconds (too slow).