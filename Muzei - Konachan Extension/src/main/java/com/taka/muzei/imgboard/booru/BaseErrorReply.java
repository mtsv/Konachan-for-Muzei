package com.taka.muzei.imgboard.booru;

import com.taka.muzei.imgboard.Utils;

public class BaseErrorReply {
    public Boolean success;
    public String message;

    @Override
    public String toString() {
        return Utils.pojoToJsonString(this);
    }
}
