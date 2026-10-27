[out:json][timeout:180][maxsize:134217728];
(
  nwr(-16.755,-49.325,-16.64,-49.205)["amenity"~"^(pharmacy|fuel|hospital|clinic|doctors|theatre|cinema|arts_centre|school|university|police|fire_station|bank|atm|parking|restaurant|cafe|fast_food)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["leisure"~"^(park|garden)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["tourism"="museum"];
);
out meta geom;
