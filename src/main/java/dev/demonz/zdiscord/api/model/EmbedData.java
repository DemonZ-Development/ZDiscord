package dev.demonz.zdiscord.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EmbedData {

    private final String title;
    private final String description;
    private final String color;
    private final String thumbnailUrl;
    private final String authorName;
    private final String authorUrl;
    private final String authorIconUrl;
    private final String footerText;
    private final List<Field> fields;

    private EmbedData(Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.color = builder.color;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.authorName = builder.authorName;
        this.authorUrl = builder.authorUrl;
        this.authorIconUrl = builder.authorIconUrl;
        this.footerText = builder.footerText;
        this.fields = Collections.unmodifiableList(new ArrayList<>(builder.fields));
    }




    public String getTitle() {
        return title;
    }


    public String getDescription() {
        return description;
    }


    public String getColor() {
        return color;
    }


    public String getThumbnailUrl() {
        return thumbnailUrl;
    }


    public String getAuthorName() {
        return authorName;
    }


    public String getAuthorUrl() {
        return authorUrl;
    }


    public String getAuthorIconUrl() {
        return authorIconUrl;
    }


    public String getFooterText() {
        return footerText;
    }


    public List<Field> getFields() {
        return fields;
    }




    public static final class Field {

        private final String name;
        private final String value;
        private final boolean inline;


        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }


        public String getName() {
            return name;
        }


        public String getValue() {
            return value;
        }


        public boolean isInline() {
            return inline;
        }

        @Override
        public String toString() {
            return "Field{name='" + name + "', inline=" + inline + '}';
        }
    }




    public static final class Builder {

        private String title;
        private String description;
        private String color;
        private String thumbnailUrl;
        private String authorName;
        private String authorUrl;
        private String authorIconUrl;
        private String footerText;
        private final List<Field> fields = new ArrayList<>();


        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }


        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }


        public Builder setColor(String hexColor) {
            this.color = hexColor;
            return this;
        }


        public Builder setThumbnailUrl(String url) {
            this.thumbnailUrl = url;
            return this;
        }


        public Builder setAuthor(String name, String url, String iconUrl) {
            this.authorName = name;
            this.authorUrl = url;
            this.authorIconUrl = iconUrl;
            return this;
        }


        public Builder setFooterText(String text) {
            this.footerText = text;
            return this;
        }


        public Builder addField(String name, String value, boolean inline) {
            fields.add(new Field(name, value, inline));
            return this;
        }


        public EmbedData build() {
            return new EmbedData(this);
        }
    }
}
