package io.yile.hible;

import java.util.UUID;

public class DeviceProfile {
    /*
     * 设备服务ＵＵＩＤ号码，要与固件中的ＵＵＩＤ匹配
     */
    public final static UUID UUID_HIBLE_SERVICE =
            UUID.fromString("e1012220-0791-4fc0-9604-ea35b58cf791");

    /*
     * 开关服务的ＵＵＩＤ号，要与固件中的ＵＵＩＤ匹配
     */
    public final static UUID UUID_HIBLE_SW_CHAR =
            UUID.fromString("e1012221-0791-4fc0-9604-ea35b58cf791");
}