package tech.kayys.aqli.cli.commands.features.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure collision analysis over immutable extension metadata snapshots. */
public final class FeatureCollisionScanner {
    private FeatureCollisionScanner() { }

    public static Report scan(List<JarClaims> jars, String pipelineService, String adapterService, String modelFamilyService) {
        Map<String,List<Claim>> extensionIds=new LinkedHashMap<>(), featureIds=new LinkedHashMap<>(),
                pipelineIds=new LinkedHashMap<>(), adapterIds=new LinkedHashMap<>(), modelFamilyIds=new LinkedHashMap<>(),
                providerClasses=new LinkedHashMap<>(), adapterProviderClasses=new LinkedHashMap<>(),
                modelFamilyProviderClasses=new LinkedHashMap<>(), implementationClasses=new LinkedHashMap<>(),
                providerIds=new LinkedHashMap<>();
        for (JarClaims jar : jars == null ? List.<JarClaims>of() : jars) {
            if (jar == null) continue;
            add(extensionIds,jar.extensionId(),jar.path(),"manifest.id");
            addAll(providerClasses,jar.pipelineProviders(),jar.path(),pipelineService);
            addAll(adapterProviderClasses,jar.adapterProviders(),jar.path(),adapterService);
            addAll(modelFamilyProviderClasses,jar.modelFamilyProviders(),jar.path(),modelFamilyService);
            for (CapabilityClaim c : jar.capabilities()) {
                if (c == null) continue;
                String kind=c.kind()==null?"":c.kind();
                add(featureIds,c.id(),jar.path(),c.source());
                if ("pipeline".equals(kind)) add(pipelineIds,c.id(),jar.path(),c.source());
                else if ("adapter".equals(kind)) add(adapterIds,c.id(),jar.path(),c.source());
                else if ("model-family".equals(kind)) add(modelFamilyIds,c.id(),jar.path(),c.source());
                add(implementationClasses,c.implementationClass(),jar.path(),suffix(c.source(),".class"));
                add(providerIds,c.providerId(),jar.path(),suffix(c.source(),".provider"));
            }
        }
        List<Item> items=new ArrayList<>();
        items.addAll(items("extension_id",extensionIds)); items.addAll(items("feature_id",featureIds));
        items.addAll(items("pipeline_id",pipelineIds)); items.addAll(items("adapter_id",adapterIds));
        items.addAll(items("model_family_id",modelFamilyIds)); items.addAll(items("provider_class",providerClasses));
        items.addAll(items("adapter_provider_class",adapterProviderClasses));
        items.addAll(items("model_family_provider_class",modelFamilyProviderClasses));
        items.addAll(items("implementation_class",implementationClasses)); items.addAll(items("provider_id",providerIds));
        items.sort((l,r)->{int k=l.kind().compareTo(r.kind());return k!=0?k:l.value().compareTo(r.value());});
        return new Report(items);
    }
    private static String suffix(String source,String suffix){return (source==null||source.isBlank()?"unknown":source)+suffix;}
    private static void addAll(Map<String,List<Claim>> target,List<String> values,String path,String source){
        if(values!=null) for(String value:values) add(target,value,path,source);
    }
    private static void add(Map<String,List<Claim>> target,String value,String path,String source){
        if(value==null||value.isBlank())return;
        Claim claim=new Claim(path==null?"":path,source==null||source.isBlank()?"unknown":source);
        List<Claim> claims=target.computeIfAbsent(value.trim(),ignored->new ArrayList<>());
        if(!claims.contains(claim))claims.add(claim);
    }
    private static List<Item> items(String kind,Map<String,List<Claim>> grouped){
        List<Item> result=new ArrayList<>();
        for(var entry:grouped.entrySet()){
            Set<String> paths=new LinkedHashSet<>();
            for(Claim claim:entry.getValue())paths.add(claim.path());
            if(paths.size()>1)result.add(new Item(kind,entry.getKey(),entry.getValue()));
        }
        return result;
    }
    public record JarClaims(String path,String extensionId,List<String> pipelineProviders,List<String> adapterProviders,
            List<String> modelFamilyProviders,List<CapabilityClaim> capabilities){
        public JarClaims{
            pipelineProviders=pipelineProviders==null?List.of():List.copyOf(pipelineProviders);
            adapterProviders=adapterProviders==null?List.of():List.copyOf(adapterProviders);
            modelFamilyProviders=modelFamilyProviders==null?List.of():List.copyOf(modelFamilyProviders);
            capabilities=capabilities==null?List.of():List.copyOf(capabilities);
        }
    }
    public record CapabilityClaim(String id,String kind,String implementationClass,String providerId,String source){}
    public record Report(List<Item> items){
        public Report{items=items==null?List.of():List.copyOf(items);}
        public Map<String,Object> toMap(){
            Map<String,Object> v=new LinkedHashMap<>();v.put("count",items.size());
            v.put("status",items.isEmpty()?"ok":"warning");v.put("items",items.stream().map(Item::toMap).toList());return v;
        }
    }
    public record Item(String kind,String value,List<Claim> claims){
        public Item{claims=claims==null?List.of():List.copyOf(claims);}
        public Map<String,Object> toMap(){
            Map<String,Object> v=new LinkedHashMap<>();v.put("kind",kind);v.put("value",value);
            v.put("severity","warning");v.put("claims",claims.stream().map(Claim::toMap).toList());return v;
        }
    }
    public record Claim(String path,String source){
        public Map<String,Object> toMap(){Map<String,Object> v=new LinkedHashMap<>();v.put("path",path);v.put("source",source);return v;}
    }
}
