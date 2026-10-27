[out:json][timeout:180][maxsize:268435456];
(
  way(-16.755,-49.325,-16.64,-49.205)["highway"]["highway"!~"^(construction|proposed|abandoned)$"];
  way(-16.755,-49.325,-16.64,-49.205)["railway"~"^(rail|light_rail|tram|subway|narrow_gauge|preserved)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["waterway"];
  nwr(-16.755,-49.325,-16.64,-49.205)["natural"~"^(water|wood|scrub|grassland|wetland|heath)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["water"];
  nwr(-16.755,-49.325,-16.64,-49.205)["building"]["building"!="no"];
  nwr(-16.755,-49.325,-16.64,-49.205)["landuse"~"^(residential|commercial|retail|industrial|forest|grass|meadow|recreation_ground|cemetery|farmland|farmyard|brownfield|greenfield|allotments|village_green|basin|reservoir|orchard|plant_nursery)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["leisure"~"^(park|garden|nature_reserve|golf_course|pitch|playground|sports_centre|recreation_ground)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["place"~"^(city|town|suburb|quarter|neighbourhood|square)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["name"~"Praça C.vica|Praça Doutor Pedro|Praça Dr.*Pedro|Parque.*Vaca|Parque.*Flamboyant|^Setor Bueno$|^Centro$",i];
);
out meta geom;
