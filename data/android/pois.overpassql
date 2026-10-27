[out:json][timeout:180][maxsize:134217728];
(
  nwr(-16.755,-49.325,-16.64,-49.205)["amenity"~"^(fuel|parking|restaurant|cafe|fast_food|hospital|clinic|doctors|pharmacy)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["leisure"~"^(park|garden)$"];
  nwr(-16.755,-49.325,-16.64,-49.205)["shop"~"^(supermarket|convenience)$"];
);
out meta geom;
