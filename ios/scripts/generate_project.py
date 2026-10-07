"""Regenerate the checked-in Xcode project using Python 3 standard library only."""
from pathlib import Path
import hashlib
import json
import plistlib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
objects = {}

def uid(key):
    return hashlib.sha256(key.encode()).hexdigest()[:24].upper()

def obj(key, isa, **fields):
    ident = uid(key)
    assert ident not in objects, key
    objects[ident] = dict(isa=isa, **fields)
    return ident

def plist(path, value):
    dest = ROOT / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(plistlib.dumps(value, sort_keys=False))

def encode(value, level=0):
    if isinstance(value, dict):
        pad = '\t' * (level + 1)
        return '{\n' + ''.join(f'{pad}{encode(k)} = {encode(v, level+1)};\n' for k,v in value.items()) + '\t'*level + '}'
    if isinstance(value, list):
        return '(' + ', '.join(encode(v, level+1) for v in value) + (',' if value else '') + ')'
    if isinstance(value, int):
        return str(value)
    return json.dumps(str(value), ensure_ascii=False)

COMMON = sorted(str(p.relative_to(ROOT)).replace('\\', '/') for p in (ROOT/'Shared').glob('*.swift'))
APP = sorted(str(p.relative_to(ROOT)).replace('\\', '/') for p in (ROOT/'App').glob('*.swift'))
RESOURCES = sorted(set(str(p.relative_to(ROOT)).replace('\\', '/') for p in (ROOT/'Resources').glob('*') if p.is_file()) | {'Resources/PrivacyInfo.xcprivacy'})
TARGETS = {
    'PotatoSupervisor': dict(kind='app', sources=COMMON+APP, resources=RESOURCES, suffix='', product='PotatoSupervisor.app'),
    'StudyMonitorExtension': dict(kind='extension', sources=COMMON+['Extensions/Monitor/StudyMonitor.swift'], resources=['Config/PrivacyInfo.xcprivacy'], suffix='.Monitor', product='StudyMonitorExtension.appex', principal='StudyMonitor', point='com.apple.deviceactivity.monitor-extension'),
    'StudyShieldConfigurationExtension': dict(kind='extension', sources=COMMON+['Extensions/ShieldConfiguration/StudyShieldConfiguration.swift'], resources=['Resources/potato-mine.png','Resources/potato-mine-angry.png','Resources/dialogue.json','Resources/THIRD_PARTY_NOTICES.txt','Config/PrivacyInfo.xcprivacy'], suffix='.ShieldConfiguration', product='StudyShieldConfigurationExtension.appex', principal='StudyShieldConfiguration', point='com.apple.ManagedSettingsUI.shield-configuration-service'),
    'StudyShieldActionExtension': dict(kind='extension', sources=COMMON+['Extensions/ShieldAction/StudyShieldAction.swift'], resources=['Config/PrivacyInfo.xcprivacy'], suffix='.ShieldAction', product='StudyShieldActionExtension.appex', principal='StudyShieldAction', point='com.apple.ManagedSettings.shield-action-service'),
    'PolicyTests': dict(kind='test', sources=['Shared/Policy.swift','Tests/PolicyTests.swift'], resources=[], suffix='.PolicyTests', product='PolicyTests.xctest'),
}
privacy = dict(NSPrivacyTracking=False, NSPrivacyCollectedDataTypes=[], NSPrivacyAccessedAPITypes=[
    dict(NSPrivacyAccessedAPIType='NSPrivacyAccessedAPICategorySystemBootTime', NSPrivacyAccessedAPITypeReasons=['35F9.1'])])
plist('Config/PrivacyInfo.xcprivacy', privacy)
plist('Resources/PrivacyInfo.xcprivacy', privacy)
for name, target in TARGETS.items():
    info = dict(CFBundleDevelopmentRegion='zh_CN', CFBundleExecutable='$(EXECUTABLE_NAME)', CFBundleIdentifier='$(PRODUCT_BUNDLE_IDENTIFIER)', CFBundleInfoDictionaryVersion='6.0', CFBundleName='$(PRODUCT_NAME)', CFBundlePackageType='APPL' if target['kind']=='app' else 'BNDL' if target['kind']=='test' else 'XPC!', CFBundleShortVersionString='$(MARKETING_VERSION)', CFBundleVersion='$(CURRENT_PROJECT_VERSION)')
    if target['kind'] != 'test':
        info['PotatoAppGroup'] = '$(APP_GROUP_IDENTIFIER)'
        plist(f'Config/{name}.entitlements', {'com.apple.developer.family-controls':True, 'com.apple.security.application-groups':['$(APP_GROUP_IDENTIFIER)']})
    if target['kind']=='app':
        info.update(CFBundleDisplayName='土豆监督员', LSRequiresIPhoneOS=True, UILaunchScreen={}, UIApplicationSceneManifest={'UIApplicationSupportsMultipleScenes':False}, UISupportedInterfaceOrientations=['UIInterfaceOrientationPortrait','UIInterfaceOrientationLandscapeLeft','UIInterfaceOrientationLandscapeRight'])
    elif target['kind']=='extension':
        info['NSExtension'] = {'NSExtensionPointIdentifier':target['point'], 'NSExtensionPrincipalClass':'$(PRODUCT_MODULE_NAME).'+target['principal']}
    plist(f'Config/{name}-Info.plist', info)

file_paths = set(COMMON+APP+RESOURCES+['Config/Project.xcconfig','Config/PrivacyInfo.xcprivacy'])
for name, target in TARGETS.items():
    file_paths.update(target['sources']+target['resources']+[f'Config/{name}-Info.plist'])
    if target['kind']!='test': file_paths.add(f'Config/{name}.entitlements')
refs = {}
types = {'.swift':'sourcecode.swift','.plist':'text.plist.xml','.entitlements':'text.plist.entitlements','.xcconfig':'text.xcconfig','.xcprivacy':'text.plist.xml','.png':'image.png','.wav':'audio.wav','.json':'text.json','.txt':'text'}
for path in sorted(file_paths):
    assert (ROOT/path).is_file(), path
    refs[path] = obj('file:'+path, 'PBXFileReference', lastKnownFileType=types.get(Path(path).suffix,'text'), path=path, sourceTree='<group>')
products = {name:obj('product:'+name,'PBXFileReference',explicitFileType={'app':'wrapper.application','extension':'wrapper.app-extension','test':'wrapper.cfbundle'}[t['kind']],includeInIndex=0,path=t['product'],sourceTree='BUILT_PRODUCTS_DIR') for name,t in TARGETS.items()}
products_group = obj('products','PBXGroup',children=list(products.values()),name='Products',sourceTree='<group>')
groups = []
for folder in ['App','Shared','Extensions','Tests','Resources','Config']:
    groups.append(obj('group:'+folder,'PBXGroup',children=[refs[p] for p in sorted(refs) if p.startswith(folder+'/')],name=folder,sourceTree='<group>'))
main_group = obj('main-group','PBXGroup',children=groups+[products_group],sourceTree='<group>')

def configs(key, settings):
    ids=[]
    for variant in ['Debug','Release']:
        current=dict(settings)
        current['SWIFT_OPTIMIZATION_LEVEL']='-Onone' if variant=='Debug' else '-O'
        if variant=='Debug': current.update(ENABLE_TESTABILITY='YES', SWIFT_ACTIVE_COMPILATION_CONDITIONS='DEBUG')
        ids.append(obj('config:'+key+':'+variant,'XCBuildConfiguration',baseConfigurationReference=refs['Config/Project.xcconfig'],buildSettings=current,name=variant))
    return obj('configs:'+key,'XCConfigurationList',buildConfigurations=ids,defaultConfigurationIsVisible=0,defaultConfigurationName='Release')

project_configs=configs('project',{'SDKROOT':'iphoneos','CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','DEBUG_INFORMATION_FORMAT':'dwarf-with-dsym'})
for name, target in TARGETS.items():
    sources = [obj('source:'+name+':'+p,'PBXBuildFile',fileRef=refs[p]) for p in target['sources']]
    resources = [obj('resource:'+name+':'+p,'PBXBuildFile',fileRef=refs[p]) for p in target['resources']]
    phases=[obj('sources:'+name,'PBXSourcesBuildPhase',buildActionMask=2147483647,files=sources,runOnlyForDeploymentPostprocessing=0),obj('frameworks:'+name,'PBXFrameworksBuildPhase',buildActionMask=2147483647,files=[],runOnlyForDeploymentPostprocessing=0),obj('resources:'+name,'PBXResourcesBuildPhase',buildActionMask=2147483647,files=resources,runOnlyForDeploymentPostprocessing=0)]
    dependencies=[]
    dependency_names=[n for n,t in TARGETS.items() if t['kind']=='extension'] if target['kind']=='app' else ['PotatoSupervisor'] if target['kind']=='test' else []
    for dep in dependency_names:
        proxy=obj('proxy:'+name+':'+dep,'PBXContainerItemProxy',containerPortal=uid('project'),proxyType=1,remoteGlobalIDString=uid('target:'+dep),remoteInfo=dep)
        dependencies.append(obj('dependency:'+name+':'+dep,'PBXTargetDependency',target=uid('target:'+dep),targetProxy=proxy))
    if target['kind']=='app':
        embedded=[obj('embed:'+n,'PBXBuildFile',fileRef=products[n],settings={'ATTRIBUTES':['RemoveHeadersOnCopy']}) for n in dependency_names]
        phases.append(obj('embed-extensions','PBXCopyFilesBuildPhase',buildActionMask=2147483647,dstPath='',dstSubfolderSpec=13,files=embedded,name='Embed App Extensions',runOnlyForDeploymentPostprocessing=0))
    settings={'PRODUCT_NAME':'$(TARGET_NAME)','PRODUCT_BUNDLE_IDENTIFIER':'$(APP_BUNDLE_IDENTIFIER)'+target['suffix'],'INFOPLIST_FILE':f'Config/{name}-Info.plist','GENERATE_INFOPLIST_FILE':'NO','TARGETED_DEVICE_FAMILY':'1','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','SUPPORTS_MACCATALYST':'NO','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks','@executable_path/../../Frameworks'],'SWIFT_EMIT_LOC_STRINGS':'YES'}
    if target['kind']!='test': settings['CODE_SIGN_ENTITLEMENTS']=f'Config/{name}.entitlements'
    if target['kind']=='extension': settings.update(APPLICATION_EXTENSION_API_ONLY='YES',SKIP_INSTALL='YES')
    if target['kind']=='test': settings.update(SKIP_INSTALL='YES',TEST_HOST='$(BUILT_PRODUCTS_DIR)/PotatoSupervisor.app/PotatoSupervisor',BUNDLE_LOADER='$(TEST_HOST)')
    obj('target:'+name,'PBXNativeTarget',buildConfigurationList=configs(name,settings),buildPhases=phases,buildRules=[],dependencies=dependencies,name=name,productName=name,productReference=products[name],productType={'app':'com.apple.product-type.application','extension':'com.apple.product-type.app-extension','test':'com.apple.product-type.bundle.unit-test'}[target['kind']])
obj('project','PBXProject',attributes={'LastUpgradeCheck':'2600','BuildIndependentTargetsInParallel':'YES'},buildConfigurationList=project_configs,compatibilityVersion='Xcode 14.0',developmentRegion='zh_CN',hasScannedForEncodings=0,knownRegions=['zh_CN','en','Base'],mainGroup=main_group,productRefGroup=products_group,projectDirPath='',projectRoot='',targets=[uid('target:'+n) for n in TARGETS])
project=ROOT/'PotatoSupervisor.xcodeproj'
project.mkdir(exist_ok=True)
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n'+encode({'archiveVersion':1,'classes':{},'objectVersion':56,'objects':objects,'rootObject':uid('project')})+'\n',encoding='utf-8')

def buildable(parent,name):
    ET.SubElement(parent,'BuildableReference',BuildableIdentifier='primary',BlueprintIdentifier=uid('target:'+name),BuildableName=TARGETS[name]['product'],BlueprintName=name,ReferencedContainer='container:PotatoSupervisor.xcodeproj')
scheme=ET.Element('Scheme',LastUpgradeVersion='2600',version='1.7')
build=ET.SubElement(scheme,'BuildAction',parallelizeBuildables='YES',buildImplicitDependencies='YES')
entries=ET.SubElement(build,'BuildActionEntries')
for name in ['PotatoSupervisor','PolicyTests']:
    entry=ET.SubElement(entries,'BuildActionEntry',buildForTesting='YES',buildForRunning='YES' if name=='PotatoSupervisor' else 'NO',buildForProfiling='YES' if name=='PotatoSupervisor' else 'NO',buildForArchiving='YES' if name=='PotatoSupervisor' else 'NO',buildForAnalyzing='YES')
    buildable(entry,name)
test=ET.SubElement(scheme,'TestAction',buildConfiguration='Debug',selectedDebuggerIdentifier='Xcode.DebuggerFoundation.Debugger.LLDB',selectedLauncherIdentifier='Xcode.IDEFoundation.Launcher.LLDB',shouldUseLaunchSchemeArgsEnv='YES')
buildable(ET.SubElement(test,'MacroExpansion'),'PotatoSupervisor')
buildable(ET.SubElement(ET.SubElement(test,'Testables'),'TestableReference',skipped='NO'),'PolicyTests')
launch=ET.SubElement(scheme,'LaunchAction',buildConfiguration='Debug',selectedDebuggerIdentifier='Xcode.DebuggerFoundation.Debugger.LLDB',selectedLauncherIdentifier='Xcode.IDEFoundation.Launcher.LLDB',launchStyle='0',useCustomWorkingDirectory='NO',ignoresPersistentStateOnLaunch='NO',debugDocumentVersioning='YES',debugServiceExtension='internal',allowLocationSimulation='YES')
buildable(ET.SubElement(launch,'BuildableProductRunnable',runnableDebuggingMode='0'),'PotatoSupervisor')
profile=ET.SubElement(scheme,'ProfileAction',buildConfiguration='Release',shouldUseLaunchSchemeArgsEnv='YES',savedToolIdentifier='',useCustomWorkingDirectory='NO',debugDocumentVersioning='YES')
buildable(ET.SubElement(profile,'BuildableProductRunnable',runnableDebuggingMode='0'),'PotatoSupervisor')
ET.SubElement(scheme,'AnalyzeAction',buildConfiguration='Debug')
ET.SubElement(scheme,'ArchiveAction',buildConfiguration='Release',revealArchiveInOrganizer='YES')
scheme_path=project/'xcshareddata/xcschemes/PotatoSupervisor.xcscheme'
scheme_path.parent.mkdir(parents=True,exist_ok=True)
ET.indent(scheme)
ET.ElementTree(scheme).write(scheme_path,encoding='utf-8',xml_declaration=True)
(ROOT/'Config/project-manifest.json').write_text(json.dumps({'targets':TARGETS,'objects':len(objects),'targetIDs':{n:uid('target:'+n) for n in TARGETS}},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated 5-target Xcode project and shared scheme (not an Xcode build).')
