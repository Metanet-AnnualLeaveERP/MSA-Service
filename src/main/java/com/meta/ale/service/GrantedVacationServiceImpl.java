package com.meta.ale.service;

import com.meta.ale.domain.EmpDto;
import com.meta.ale.domain.GrantedVcDto;
import com.meta.ale.domain.VcTypeDto;
import com.meta.ale.mapper.GrantedVcMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GrantedVacationServiceImpl implements GrantedVacationService {

    private final GrantedVcMapper vcMapper;
    private final EmpService empService;
    private final VcTypeService vcTypeService;


    /*
        -트리거에서 사용될 내용으로 1월 1일에 연차가 자동 생성되게 하는 로직-
        1. 1년 이상 근무자에 대해 조회(조회시 Leave_Date가 Null이며 HireDate가 현재 일로부터 1년이상)
        2. 조회 후 오늘 날짜와 비교해 현재 몇년차인지 검사.
        3. 년차에 따라 휴가를 부여 ((int) (15 + (n-1)/2))
        4. 최대 연차 부여 갯수 25
    */
    @Override
    @Transactional
    public boolean insertAnnualByEmpOverOneYr() throws Exception {
        Date date = new Date();
        LocalDate today = LocalDateTime.now().toLocalDate();
        Date expiredDate = new Date(date.getTime() + (365 * 24 * 60 * 60 * 1000L));
        //휴가 타입에 대한 정보 받아오기
        VcTypeDto vcTypeDto = vcTypeService.getVcType("연차");

        // 1년 이상 사람에 대한 연차 계산 후 부여
        boolean overOneYrList = addEmpOverOneYrList(date, expiredDate, today, vcTypeDto);
        if (overOneYrList) System.out.println("1년 이상 사람 존재 연차부여완료");
        if(!overOneYrList) System.out.println("1년 이상 사람 존재안함");
        // 1년이 된 사람들에 대한 연차 부여
        boolean oneYrList = addEmpOneYrList(date, expiredDate, today, vcTypeDto);
        if (oneYrList) System.out.println("1년이 된 사람 존재 : 연차 부여 완료");
        if(!oneYrList) System.out.println("1년 된 사람 존재안함");
        // 1년이 안된 사람들에 대한 연차 계산
        boolean underOneYrList = addEmpUnderOneYrList(date, expiredDate, today, vcTypeDto);
        if (underOneYrList) System.out.println("1년 안된 사람들 : 연차 부여 완료");
        if(!underOneYrList) System.out.println("1년 안된 사람들 존재안함");
        return true;
    }

    /* ------------------------- Private Method ------------------------- */
    // 1년이 지난 사람들중 오늘 날짜와 1년이 된 사람 대한 연차계산 방법
    private boolean addEmpOverOneYrList(Date date, Date expiredDate, LocalDate today, VcTypeDto vcTypeDto) {
        List<EmpDto> empOverOneYrList = empService.findEmpOverOneYr();

        if (empOverOneYrList.size() != 0) {
            for (EmpDto e : empOverOneYrList) {
                //근속일수 계산
                long duration = empCalcHireDate(today, e.getHireDate());
                GrantedVcDto grantedVcDto = new GrantedVcDto(null, date, expiredDate, duration, duration, vcTypeDto, e);
                vcMapper.insertAnnualGranted(grantedVcDto);
            }
            return true;
        }
        return false;
    }
    // 오늘날로부터 딱 1년인 사람의 연차 부여
    private boolean addEmpOneYrList(Date date, Date expiredDate, LocalDate today, VcTypeDto vcTypeDto) {

        List<EmpDto> empOneYrList = empService.findEmpOneYr();
        //1년인 사람에 대한 연차계산 방법
        GrantedVcDto grantedVcDto = new GrantedVcDto();
        if (empOneYrList.size() != 0) {
            for (EmpDto e : empOneYrList) {
                grantedVcDto.setEmpDto(e);
                grantedVcDto.setVcTypeDto(vcTypeDto);

                //1년인사람 중 연차이면서 EMPID가 e.getEmpId 인 사람의 휴가를 추가
                GrantedVcDto grantedVcDtoToDB = vcMapper.findByEmpIdVcType(grantedVcDto);

                Long vcDays = grantedVcDtoToDB.getVcDays();
                Long remainDays = grantedVcDtoToDB.getRemainDays();
                //휴가가 새롭게 부여되기 때문에 부여일자와 만료일자를 부여된 시점으로 부터 초기화
                // 1년이 된 사람은 옛날엔 사용갯수에 대해 차감했는데 22년 5월 이후부턴 법적으로
                // 기존에 대한 연차를 놔두고 15개가 부여되기 때문에 하드코딩으로 처리했음
                grantedVcDtoToDB.setVcDays(vcDays + 15L);
                grantedVcDtoToDB.setRemainDays(remainDays + 15L);
                grantedVcDtoToDB.setGrantedDate(date);
                grantedVcDtoToDB.setExpiredDate(expiredDate);
                vcMapper.updateAnnualGranted(grantedVcDtoToDB);
            }
            return true;
        }
        return false;

    }
    // 오늘날로부터 1년이 안된 사람들 중 한 달 간격인 된 사람들
    private boolean addEmpUnderOneYrList(Date date, Date expiredDate, LocalDate today, VcTypeDto vcTypeDto) {

        // 사원들 중 1년이 안되었으면서 n달이 된 사람들
        List<EmpDto> empUnderOneYrList = empService.findEmpUnderOneYr();
        GrantedVcDto grantedVcDto = new GrantedVcDto();
        if (empUnderOneYrList.size() != 0) {
            for (EmpDto e : empUnderOneYrList) {
                grantedVcDto.setEmpDto(e);
                grantedVcDto.setVcTypeDto(vcTypeDto);
                GrantedVcDto grantedVcDtoToDB = vcMapper.findByEmpIdVcType(grantedVcDto);

                if (grantedVcDtoToDB != null && grantedVcDtoToDB.getVcId() != null) {
                    Long vcDays = grantedVcDtoToDB.getVcDays();
                    Long remainDays = grantedVcDtoToDB.getRemainDays();
                    grantedVcDtoToDB.setGrantedDate(date);
                    grantedVcDtoToDB.setExpiredDate(date);
                    grantedVcDtoToDB.setRemainDays(vcDays + 1L);
                    grantedVcDtoToDB.setRemainDays(remainDays + 1L);
                    grantedVcDtoToDB.setVcTypeDto(vcTypeDto);
                    System.out.println(grantedVcDtoToDB);
                    vcMapper.updateAnnualGranted(grantedVcDtoToDB);
                } else {
                    grantedVcDto = new GrantedVcDto(null, date, expiredDate, 1L, 1L, vcTypeDto, e);
                    vcMapper.insertAnnualGranted(grantedVcDto);
                }
            }
            return true;
        }
        return false;
    }

    //입사일로부터 현재까지 경력 계산하고 연차 갯수 계산
    private long empCalcHireDate(LocalDate today, Date hireDate) {
        LocalDate localHireDate = hireDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        Period period = Period.between(localHireDate, today);
        int days = period.getDays();
        int months = period.getMonths() * 30;
        int years = period.getYears() * 365;
        int date = (days + months + years) / 365;
        long vacationDays = (long) (15 + ((double) (date - 1) / 2));

        return vacationDays > 25 ? 25L : vacationDays;
    }


}