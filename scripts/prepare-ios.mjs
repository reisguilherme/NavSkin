import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import sharp from 'sharp';

const projectRoot = path.resolve(import.meta.dirname, '..');
const ios = path.join(projectRoot, 'ios');
const resources = path.join(ios, 'NavSkin/Resources');
const androidAssets = path.join(projectRoot, 'android/app/src/main/assets');
const id = value => createHash('sha1').update(value).digest('hex').slice(0, 24).toUpperCase();
const quote = value => JSON.stringify(value);
async function walk(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? walk(path.join(directory, entry.name)) : path.join(directory, entry.name)))).flat().sort();
}

export async function prepareAssets() {
  await fs.mkdir(resources, { recursive: true });
  for (const directory of ['maps', 'navigation-icons', 'game-assets']) {
    await fs.cp(path.join(androidAssets, directory), path.join(resources, directory), { recursive: true });
  }
  for (const theme of ['gta', 'rosa', 'rdr2']) {
    await sharp(path.join(resources, `maps/previews/${theme}.webp`)).png()
      .toFile(path.join(resources, `maps/previews/${theme}.png`));
  }
  const iconDirectory = path.join(ios, 'NavSkin/Assets.xcassets/AppIcon.appiconset');
  await fs.mkdir(iconDirectory, { recursive: true });
  const icon = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><rect width="1024" height="1024" fill="#FFFCF4"/><path d="M180 276 400 190 624 276 844 190V748L624 834 400 748 180 834Z" fill="#E8EEDB" stroke="#4C692B" stroke-width="42" stroke-linejoin="round"/><path d="M400 190V748M624 276V834" fill="none" stroke="#4C692B" stroke-width="32"/><path d="M512 356C419 356 348 425 348 513c0 118 164 240 164 240s164-122 164-240c0-88-71-157-164-157Z" fill="#4C692B" stroke="#FFFCF4" stroke-width="22"/><circle cx="512" cy="510" r="48" fill="#FFFCF4"/></svg>`;
  await sharp(Buffer.from(icon)).png().toFile(path.join(iconDirectory, 'AppIcon.png'));
  await fs.writeFile(path.join(iconDirectory, 'Contents.json'), JSON.stringify({ images: [{ filename: 'AppIcon.png', idiom: 'universal', platform: 'ios', size: '1024x1024' }], info: { author: 'xcode', version: 1 } }, null, 2));
  await fs.writeFile(path.join(ios, 'NavSkin/Assets.xcassets/Contents.json'), JSON.stringify({ info: { author: 'xcode', version: 1 } }, null, 2));
  const files = [];
  for (const file of await walk(resources)) {
    if (path.basename(file) === 'bundle-manifest.json') continue;
    const bytes = await fs.readFile(file);
    files.push({ path: path.relative(resources, file).replaceAll('\\', '/'), bytes: bytes.length,
      sha256: createHash('sha256').update(bytes).digest('hex') });
  }
  await fs.writeFile(path.join(resources, 'bundle-manifest.json'), JSON.stringify({ schemaVersion: 1,
    origin: 'Shared NavSkin world styles, Goiânia offline cartography and original navigation artwork.', files }, null, 2));
  return files.length;
}

export async function generateProject() {
  await fs.mkdir(path.join(ios, 'NavSkin.xcodeproj/xcshareddata/xcschemes'), { recursive: true });
  const appSources = (await walk(path.join(ios, 'NavSkin'))).filter(file => file.endsWith('.swift'));
  const testSources = (await walk(path.join(ios, 'NavSkinTests'))).filter(file => file.endsWith('.swift'));
  const objects = [];
  const add = (name, body) => { const key = id(name); objects.push(`${key} = { ${body} };`); return key; };
  const ref = (name, type, file) => add(`file:${name}`, `isa = PBXFileReference; lastKnownFileType = ${type}; path = ${quote(file)}; sourceTree = "<group>";`);
  const buildFiles = files => files.map(file => {
    const relative = path.relative(ios, file).replaceAll('\\', '/');
    const fileRef = ref(relative, 'sourcecode.swift', relative);
    return { fileRef, build: add(`build:${relative}`, `isa = PBXBuildFile; fileRef = ${fileRef};`) };
  });
  const sources = buildFiles(appSources);
  const tests = buildFiles(testSources);
  const mapResources = ref('Resources', 'folder', 'NavSkin/Resources');
  const assets = ref('Assets', 'folder.assetcatalog', 'NavSkin/Assets.xcassets');
  const privacy = ref('Privacy', 'text.xml', 'NavSkin/PrivacyInfo.xcprivacy');
  const plist = ref('Info', 'text.plist.xml', 'NavSkin/Info.plist');
  const signing = ref('Signing', 'text.xcconfig', 'Config/Signing.xcconfig');
  const resourceBuilds = [mapResources, assets, privacy].map(file => add(`resource:${file}`, `isa = PBXBuildFile; fileRef = ${file};`));
  const remotePackage = add('MapLibrePackage', 'isa = XCRemoteSwiftPackageReference; repositoryURL = "https://github.com/maplibre/maplibre-gl-native-distribution"; requirement = { kind = exactVersion; version = 6.31.0; };');
  const localPackage = add('CorePackage', 'isa = XCLocalSwiftPackageReference; relativePath = Packages/NavSkinCore;');
  const mapLibre = add('MapLibreProduct', `isa = XCSwiftPackageProductDependency; package = ${remotePackage}; productName = MapLibre;`);
  const core = add('CoreProduct', `isa = XCSwiftPackageProductDependency; package = ${localPackage}; productName = NavSkinCore;`);
  const coreTest = add('CoreTestProduct', `isa = XCSwiftPackageProductDependency; package = ${localPackage}; productName = NavSkinCore;`);
  const frameworkBuilds = [mapLibre, core].map(product => add(`framework:${product}`, `isa = PBXBuildFile; productRef = ${product};`));
  const testFramework = add('TestCoreFramework', `isa = PBXBuildFile; productRef = ${coreTest};`);
  const appProduct = add('AppProduct', 'isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = NavSkin.app; sourceTree = BUILT_PRODUCTS_DIR;');
  const testProduct = add('TestsProduct', 'isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = NavSkinTests.xctest; sourceTree = BUILT_PRODUCTS_DIR;');
  const phase = (name, kind, files) => add(name, `isa = ${kind}; buildActionMask = 2147483647; files = (${files.join(',')}); runOnlyForDeploymentPostprocessing = 0;`);
  const sourcePhase = phase('AppSources', 'PBXSourcesBuildPhase', sources.map(source => source.build));
  const testSourcePhase = phase('TestSources', 'PBXSourcesBuildPhase', tests.map(source => source.build));
  const resourcesPhase = phase('ResourcesPhase', 'PBXResourcesBuildPhase', resourceBuilds);
  const frameworksPhase = phase('FrameworksPhase', 'PBXFrameworksBuildPhase', frameworkBuilds);
  const testFrameworks = phase('TestFrameworks', 'PBXFrameworksBuildPhase', [testFramework]);
  const productsGroup = add('ProductsGroup', `isa = PBXGroup; children = (${appProduct},${testProduct}); name = Products; sourceTree = "<group>";`);
  const mainGroup = add('MainGroup', `isa = PBXGroup; children = (${sources.map(source => source.fileRef).join(',')},${tests.map(source => source.fileRef).join(',')},${mapResources},${assets},${privacy},${plist},${signing},${productsGroup}); sourceTree = "<group>";`);
  const settings = values => Object.entries(values).map(([key, value]) => `${quote(key)} = ${Array.isArray(value) ? `(${value.map(quote).join(',')})` : quote(value)};`).join(' ');
  function configurationList(name, base, withSigning = false) {
    const configs = ['Debug', 'Release'].map(configuration => {
      const config = { ...base, SWIFT_OPTIMIZATION_LEVEL: configuration === 'Debug' ? '-Onone' : '-O',
        DEBUG_INFORMATION_FORMAT: configuration === 'Debug' ? 'dwarf' : 'dwarf-with-dsym',
        SWIFT_ACTIVE_COMPILATION_CONDITIONS: configuration === 'Debug' ? 'DEBUG $(inherited)' : '$(inherited)',
        ENABLE_TESTABILITY: configuration === 'Debug' ? 'YES' : 'NO' };
      return add(`${name}:${configuration}`, `isa = XCBuildConfiguration; ${withSigning ? `baseConfigurationReference = ${signing};` : ''} buildSettings = { ${settings(config)} }; name = ${configuration};`);
    });
    return add(`${name}:configList`, `isa = XCConfigurationList; buildConfigurations = (${configs.join(',')}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;`);
  }
  const projectConfigurations = configurationList('Project', { CLANG_ENABLE_MODULES: 'YES', SDKROOT: 'iphoneos', IPHONEOS_DEPLOYMENT_TARGET: '17.0', SWIFT_VERSION: '5.0', SWIFT_STRICT_CONCURRENCY: 'minimal' });
  const appConfigurations = configurationList('Application', { PRODUCT_NAME: 'NavSkin', PRODUCT_MODULE_NAME: 'NavSkin', MARKETING_VERSION: '0.7.0', CURRENT_PROJECT_VERSION: '1', INFOPLIST_FILE: 'NavSkin/Info.plist', GENERATE_INFOPLIST_FILE: 'NO', ASSETCATALOG_COMPILER_APPICON_NAME: 'AppIcon', CODE_SIGN_STYLE: 'Automatic', TARGETED_DEVICE_FAMILY: '1,2', SUPPORTED_PLATFORMS: 'iphoneos iphonesimulator', SUPPORTS_MACCATALYST: 'NO', LD_RUNPATH_SEARCH_PATHS: ['$(inherited)', '@executable_path/Frameworks'] }, true);
  const testConfigurations = configurationList('Tests', { PRODUCT_NAME: 'NavSkinTests', PRODUCT_BUNDLE_IDENTIFIER: 'br.com.navskin.ios.tests', GENERATE_INFOPLIST_FILE: 'YES', CODE_SIGN_STYLE: 'Automatic', TARGETED_DEVICE_FAMILY: '1,2', TEST_HOST: '$(BUILT_PRODUCTS_DIR)/NavSkin.app/NavSkin', BUNDLE_LOADER: '$(TEST_HOST)', LD_RUNPATH_SEARCH_PATHS: ['$(inherited)', '@executable_path/Frameworks', '@loader_path/Frameworks'] }, true);
  const proxy = add('TestProxy', `isa = PBXContainerItemProxy; containerPortal = ${id('Project')}; proxyType = 1; remoteGlobalIDString = ${id('Application')}; remoteInfo = NavSkin;`);
  const targetDependency = add('TestDependency', `isa = PBXTargetDependency; target = ${id('Application')}; targetProxy = ${proxy};`);
  const appTarget = add('Application', `isa = PBXNativeTarget; buildConfigurationList = ${appConfigurations}; buildPhases = (${sourcePhase},${frameworksPhase},${resourcesPhase}); buildRules = (); dependencies = (); name = NavSkin; packageProductDependencies = (${core},${mapLibre}); productName = NavSkin; productReference = ${appProduct}; productType = "com.apple.product-type.application";`);
  const testTarget = add('Tests', `isa = PBXNativeTarget; buildConfigurationList = ${testConfigurations}; buildPhases = (${testSourcePhase},${testFrameworks}); buildRules = (); dependencies = (${targetDependency}); name = NavSkinTests; packageProductDependencies = (${coreTest}); productName = NavSkinTests; productReference = ${testProduct}; productType = "com.apple.product-type.bundle.unit-test";`);
  const project = add('Project', `isa = PBXProject; attributes = { BuildIndependentTargetsInParallel = YES; LastSwiftUpdateCheck = 1600; LastUpgradeCheck = 1600; TargetAttributes = { ${appTarget} = { CreatedOnToolsVersion = 16.0; }; ${testTarget} = { CreatedOnToolsVersion = 16.0; TestTargetID = ${appTarget}; }; }; }; buildConfigurationList = ${projectConfigurations}; compatibilityVersion = "Xcode 14.0"; developmentRegion = "pt-BR"; hasScannedForEncodings = 0; knownRegions = ("pt-BR",en,Base); mainGroup = ${mainGroup}; packageReferences = (${localPackage},${remotePackage}); productRefGroup = ${productsGroup}; projectDirPath = ""; projectRoot = ""; targets = (${appTarget},${testTarget});`);
  await fs.writeFile(path.join(ios, 'NavSkin.xcodeproj/project.pbxproj'), `// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n${objects.join('\n')}\n}; rootObject = ${project}; }\n`);
  const buildRef = (identifier, name) => `<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="${identifier}" BuildableName="${name}" BlueprintName="${name.replace(/\.(app|xctest)$/, '')}" ReferencedContainer="container:NavSkin.xcodeproj"/>`;
  const appRef = buildRef(appTarget, 'NavSkin.app');
  const testRef = buildRef(testTarget, 'NavSkinTests.xctest');
  await fs.writeFile(path.join(ios, 'NavSkin.xcodeproj/xcshareddata/xcschemes/NavSkin.xcscheme'), `<?xml version="1.0" encoding="UTF-8"?><Scheme LastUpgradeVersion="1600" version="1.7"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">${appRef}</BuildActionEntry><BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO">${testRef}</BuildActionEntry></BuildActionEntries></BuildAction><TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">${testRef}</TestableReference></Testables><MacroExpansion>${appRef}</MacroExpansion></TestAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">${appRef}</BuildableProductRunnable></LaunchAction><ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">${appRef}</BuildableProductRunnable></ProfileAction><AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>\n`);
  return appSources.length;
}

if (process.argv[1] && path.resolve(process.argv[1]) === import.meta.filename) {
  const files = process.argv.includes('--project-only') ? null : await prepareAssets();
  const sources = await generateProject();
  console.log(`iOS project prepared: ${sources} Swift app sources${files == null ? '' : `, ${files} bundled resources`}. Xcode build not executed.`);
}
