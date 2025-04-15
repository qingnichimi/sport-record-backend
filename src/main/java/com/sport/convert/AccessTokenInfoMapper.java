package com.sport.convert;

import com.sport.dto.AccessTokenDTO;
import com.sport.vo.AccessTokenInfoVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper()
public interface AccessTokenInfoMapper {
    AccessTokenInfoMapper INSTANCE = Mappers.getMapper(AccessTokenInfoMapper.class);

    AccessTokenInfoVO accessTokenDTO2VO(AccessTokenDTO accessTokenDTO);
}
