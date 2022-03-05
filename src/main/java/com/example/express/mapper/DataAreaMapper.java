package com.example.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.express.domain.bean.DataArea;
import com.example.express.domain.bean.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataAreaMapper extends BaseMapper<DataArea> {
}
