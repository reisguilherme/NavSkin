const normalize = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
export function findPlace(places, name) {
  const matches = places?.features.filter(feature => normalize(`${feature.properties.name} ${feature.properties.osm_name || ''}`).includes(normalize(name))) || [];
  // Verified anchors win over homonymous neighborhoods (notably Flamboyant).
  return matches.find(feature => feature.properties.featured === true) || matches[0];
}
