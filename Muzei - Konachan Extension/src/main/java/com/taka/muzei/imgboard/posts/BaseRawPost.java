package com.taka.muzei.imgboard.posts;

import com.taka.muzei.imgboard.Utils;

public abstract class BaseRawPost {
    public int id;

    @Override
    public String toString() {
        return Utils.pojoToJsonString(this);
    }
}
