package com.reForm.backend.form.entity;

import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.AbstractBlockJacksonConfig;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.entity.block.staticblock.standard.ShortTextStaticBlock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractBlockConverterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new AbstractBlockJacksonConfig().abstractBlockModule())
            .build();
    private final AbstractBlockConverter converter = new AbstractBlockConverter(objectMapper);

    @Test
    void roundTripsAStaticBlock() {
        ShortTextStaticBlock block = new ShortTextStaticBlock();
        block.setLabel("Name");

        String json = converter.convertToDatabaseColumn(List.of(block));
        List<AbstractBlock> roundTripped = converter.convertToEntityAttribute(json);

        assertThat(roundTripped).hasSize(1);
        assertThat(roundTripped.get(0)).isInstanceOf(ShortTextStaticBlock.class);
        assertThat(roundTripped.get(0).getLabel()).isEqualTo("Name");
    }

    @Test
    void roundTripsAMixedStaticAndConversationalList() {
        ShortTextStaticBlock staticBlock = new ShortTextStaticBlock();
        staticBlock.setLabel("Name");

        ConversationalBlock conversationalBlock = new ConversationalBlock();
        conversationalBlock.setLabel("Chat");
        conversationalBlock.setPrompt("Tell me about yourself");

        String json = converter.convertToDatabaseColumn(List.of(staticBlock, conversationalBlock));
        List<AbstractBlock> roundTripped = converter.convertToEntityAttribute(json);

        assertThat(roundTripped).hasSize(2);
        assertThat(roundTripped.get(0)).isInstanceOf(ShortTextStaticBlock.class);
        assertThat(roundTripped.get(1)).isInstanceOf(ConversationalBlock.class);
        assertThat(((ConversationalBlock) roundTripped.get(1)).getPrompt()).isEqualTo("Tell me about yourself");
    }

    @Test
    void roundTripsAnEmptyList() {
        String json = converter.convertToDatabaseColumn(List.of());
        List<AbstractBlock> roundTripped = converter.convertToEntityAttribute(json);

        assertThat(roundTripped).isEmpty();
    }

    @Test
    void convertToDatabaseColumnReturnsNullForNullAttribute() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttributeReturnsEmptyListForNullOrEmptyColumn() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }
}
