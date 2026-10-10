import os
import re

replacements = {
    r'\.architectures\(\)': '.getArchitectures()',
    r'\.modelType\(\)': '.getModelType()',
    r'\.hiddenSize\(\)': '.getHiddenSize()',
    r'\.numHiddenLayers\(\)': '.getNumHiddenLayers()',
    r'\.numAttentionHeads\(\)': '.getNumAttentionHeads()',
    r'\.numKeyValueHeads\(\)': '.getNumKeyValueHeads()',
    r'\.intermediateSize\(\)': '.getIntermediateSize()',
    r'\.vocabSize\(\)': '.getVocabSize()',
    r'\.maxPositionEmbeddings\(\)': '.getMaxPositionEmbeddings()',
    r'\.rmsNormEps\(\)': '.getRmsNormEps()',
    r'\.layerNormEps\(\)': '.getLayerNormEps()',
    r'\.ropeTheta\(\)': '.getRopeTheta()',
    r'\.ropeScaling\(\)': '.getRopeScaling()',
    r'\.layerTypes\(\)': '.getLayerTypes()',
    r'\.ropeThetaFull\(\)': '.getRopeThetaFull()',
    r'\.ropeThetaSliding\(\)': '.getRopeThetaSliding()',
    r'\.queryPreAttnScalar\(\)': '.getQueryPreAttnScalar()',
    r'\.attnLogitSoftcapping\(\)': '.getAttnLogitSoftcapping()',
    r'\.finalLogitSoftcapping\(\)': '.getFinalLogitSoftcapping()',
    r'\.hiddenAct\(\)': '.getHiddenAct()',
    r'\.bosTokenId\(\)': '.getBosTokenId()',
    r'\.eosTokenId\(\)': '.getEosTokenId()',
    r'\.padTokenId\(\)': '.getPadTokenId()',
    r'\.tieWordEmbeddings\(\)': '.isTieWordEmbeddings()',
    r'\.numExpertsPerTok\(\)': '.getNumExpertsPerTok()',
    r'\.numLocalExperts\(\)': '.getNumLocalExperts()',
    r'\.moeIntermediateSize\(\)': '.getMoeIntermediateSize()',
    r'\.visionConfig\(\)': '.getVisionConfig()',
    r'\.primaryArchitecture\(\)': '.getPrimaryArchitecture()',
    r'\.ggufArchitectureClassName\(\)': '.getGgufArchitectureClassName()',
    r'\.resolvedHeadDim\(\)': '.getResolvedHeadDim()',
    r'\.slidingWindowSize\(\)': '.getSlidingWindowSize()',
    r'\.resolvedNumKvSharedLayers\(\)': '.getResolvedNumKvSharedLayers()',
    r'\.hiddenSizePerLayerInput\(\)': '.getHiddenSizePerLayerInput()',
    r'\.vocabSizePerLayerInput\(\)': '.getVocabSizePerLayerInput()',
}

# Methods with arguments
replacements_with_args = {
    r'\.layerType\((.*?)\)': r'.getLayerType(\1)',
    r'\.sharedKvSourceLayer\((.*?)\)': r'.getSharedKvSourceLayer(\1)'
}

for root, _, files in os.walk('.'):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()

            new_content = content
            for old, new in replacements.items():
                new_content = re.sub(old, new, new_content)
            for old, new in replacements_with_args.items():
                new_content = re.sub(old, new, new_content)

            if new_content != content:
                with open(filepath, 'w') as f:
                    f.write(new_content)
                print(f"Replaced getters in {filepath}")
