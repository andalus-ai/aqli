#!/bin/bash
#
# 1. See how the dispatcher selects MR8, MR4, and base kernels
sed -n '1101,1214p' \
  backend/metal/aljabr-backend-metal/src/main/cpp/metal/aljabr_metal_matvec_api.m

# 2. Inspect triple-batch buffer allocation, dispatch, and output copies
sed -n '1332,1403p' \
  backend/metal/aljabr-backend-metal/src/main/cpp/metal/aljabr_metal_matvec_api.m

# 3. Find the actual Q4_K batch kernel implementations and pipeline setup
rg -n -C 5 \
  'matvec_tb_q4_k|MR8|mr8|MR4|mr4' \
  backend/metal/aljabr-backend-metal/src/main/cpp/metal

# 4. Show how the failing test prepares batch inputs and compares outputs
sed -n '45,175p' \
  backend/metal/aljabr-backend-metal/src/test/java/tech/kayys/aljabr/metal/binding/MetalTripleBatchCorrectnessTest.java