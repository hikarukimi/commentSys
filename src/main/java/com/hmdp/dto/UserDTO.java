package com.hmdp.dto;

import com.hmdp.entity.User;
import lombok.Data;

/**
 * @author Hikarukimi
 */
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;

    public static UserDTO entityToDto(User user){
        UserDTO userDTO=new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        userDTO.setIcon(user.getIcon());
        return userDTO;
    }
}
