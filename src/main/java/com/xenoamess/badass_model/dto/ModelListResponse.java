package com.xenoamess.badass_model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelListResponse {

    private String object;
    private List<Model> data;

    public ModelListResponse() {
    }

    public ModelListResponse(String object, List<Model> data) {
        this.object = object;
        this.data = data;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<Model> getData() {
        return data;
    }

    public void setData(List<Model> data) {
        this.data = data;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Model {
        private String id;
        private String object;
        private Long created;
        private String ownedBy;

        public Model() {
        }

        public Model(String id, String object, Long created, String ownedBy) {
            this.id = id;
            this.object = object;
            this.created = created;
            this.ownedBy = ownedBy;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public Long getCreated() {
            return created;
        }

        public void setCreated(Long created) {
            this.created = created;
        }

        public String getOwnedBy() {
            return ownedBy;
        }

        public void setOwnedBy(String ownedBy) {
            this.ownedBy = ownedBy;
        }
    }
}
