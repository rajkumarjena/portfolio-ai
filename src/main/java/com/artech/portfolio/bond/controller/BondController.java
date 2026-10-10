package com.artech.portfolio.bond.controller;

import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.service.BondService;
import com.artech.portfolio.bond.util.BondConstants;
import com.artech.portfolio.common.controller.AppParamController;
import com.artech.portfolio.common.entity.AppParamConfig;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bonds")
public class BondController {

    private final BondService bondService;
    private final AppParamController appParamController;

    public BondController(BondService bondService,
                          AppParamController appParamController) {
        this.bondService = bondService;
        this.appParamController = appParamController;
    }

    @PostMapping
    public Bond saveBond(@RequestBody Bond bond) {
        return bondService.saveBond(bond);
    }

    @GetMapping
    public List<Bond> getAllBonds() {
        return bondService.getAllBonds();
    }

    @PutMapping("/{id}")
    public Bond updateBond(@PathVariable Long id,
                           @RequestBody Bond bondDetails) {
        return bondService.updateBond(id, bondDetails);
    }

    @DeleteMapping("/{id}")
    public void deleteBond(@PathVariable Long id) {

        bondService.deleteBond(id);
    }

    @GetMapping("/{id}")
    public Bond getBondById(@PathVariable Long id) {

        return bondService.getBondById(id);
    }

    @GetMapping("/issuer")
    public List<AppParamConfig> getBondIssuer() {

        return appParamController.getConfig().stream().filter(x ->
                x.getInvestmentType().equalsIgnoreCase(BondConstants.BOND)
                        && x.getParamName().equalsIgnoreCase(BondConstants.ISSUER)).toList();
    }

    @GetMapping("/credit/rating")
    public List<AppParamConfig> getBondCreditRating() {

        return appParamController.getConfig().stream()
                .filter(x -> x.getInvestmentType()
                        .equalsIgnoreCase(BondConstants.BOND) && x.getParamName()
                        .equalsIgnoreCase(BondConstants.CREDIT_RATING))
                .toList();
    }
}
