package com.meta.ale.service;

import com.meta.ale.domain.Criteria;
import com.meta.ale.domain.UsePlanDto;
import com.meta.ale.domain.UserDto;

import java.util.Map;

public interface UsePlanService {
    public Map<String, Object> getUsePlanList(Criteria criteria, UserDto userDto) throws Exception;

    public UsePlanDto getUsePlanByPlanId(Long planId) throws Exception;

    public boolean modifyUsePlan(UsePlanDto usePlanDto) throws Exception;

    public boolean addUsePlan(UsePlanDto usePlanDto) throws Exception;
}
