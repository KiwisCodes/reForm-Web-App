package com.reForm.backend.ai.strategy.block;

import com.reForm.backend.form.entity.block.BlockType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BLOCK EXECUTION REGISTRY (Strategy Pattern Registry)
 * 
 * ROLE IN ARCHITECTURE:
 * Auto-wires all Spring @Component beans implementing IBlockExecutionStrategy
 * into a lookup Map keyed by BlockType.
 * 
 * WHY THIS IS SCALABLE:
 * Adding a new block type in the future requires zero changes to SessionContextService or
 * voice adapters — simply implement IBlockExecutionStrategy and annotate with @Component!
 */
@Slf4j
@Service
public class BlockExecutionRegistry {

    private final Map<BlockType, IBlockExecutionStrategy> strategyMap;
    private final StaticBlockExecutionStrategy defaultFallbackStrategy = new StaticBlockExecutionStrategy();

    public BlockExecutionRegistry(List<IBlockExecutionStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                    s -> s.supports(BlockType.CONVERSATIONAL) ? BlockType.CONVERSATIONAL : BlockType.STATIC,
                    Function.identity(),
                    (existing, replacement) -> existing
                ));
        log.info("✅ [BLOCK EXECUTION REGISTRY INITIALIZED]: Registered {} strategies", strategyMap.size());
    }

    /**
     * Resolves the appropriate execution strategy for the given block type.
     */
    public IBlockExecutionStrategy resolve(BlockType blockType) {
        if (blockType == null) {
            return defaultFallbackStrategy;
        }
        return strategyMap.getOrDefault(blockType, defaultFallbackStrategy);
    }
}
