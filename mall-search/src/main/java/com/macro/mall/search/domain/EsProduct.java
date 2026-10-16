package com.macro.mall.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.CompletionField;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.suggest.Completion;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 搜索商品的信息
 * Created by macro on 2018/6/19.
 */
@Data
@EqualsAndHashCode
// 忽略 ES 文档中 Spring Data 附加的 _class 类型提示等未知字段，避免底层客户端反序列化报错
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "pms")
@Setting(shards = 1,replicas = 0)
public class EsProduct implements Serializable {
    private static final long serialVersionUID = -1L;
    @Id
    private Long id;
    @Field(type = FieldType.Keyword)
    private String productSn;
    private Long brandId;
    @Field(type = FieldType.Keyword)
    private String brandName;
    private Long productCategoryId;
    @Field(type = FieldType.Keyword)
    private String productCategoryName;
    private String pic;
    @Field(analyzer = "ik_max_word",type = FieldType.Text)
    private String name;
    @Field(analyzer = "ik_max_word",type = FieldType.Text)
    private String subTitle;
    @Field(analyzer = "ik_max_word",type = FieldType.Text)
    private String keywords;
    private BigDecimal price;
    private Integer sale;
    private Integer newStatus;
    private Integer recommandStatus;
    private Integer stock;
    private Integer promotionType;
    private Integer sort;
    @Field(type = FieldType.Nested, fielddata = true)
    private List<EsProductAttributeValue> attrValueList;
    /**
     * 搜索建议（completion）输入：商品名称 + 副标题 + 关键词
     * <p>
     * 由 {@code EsProductServiceImpl} 在导入 / 创建商品时填充，
     * 供搜索框联想（suggest）使用。
     */
    @CompletionField(maxInputLength = 100)
    private Completion suggest;
}
