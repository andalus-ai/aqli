package tech.kayys.aqli.runtime.kv;

/**
 * KV must be:
 * - shared across steps
 * - partition-aware
 * - possibly remote
 * 
 * Implementations:
 * - LocalKVStore
 * - RemoteKVStore
 * - ShardedKVStore
 */
public interface KVStore {
    KVBlock get(int pos);

    void put(int pos, KVBlock block);
}