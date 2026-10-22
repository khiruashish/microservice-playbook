package dev.foodfleet.ch20.detect;

import dev.foodfleet.ch20.model.SystemModel;
import java.util.List;

@FunctionalInterface
public interface Detector {

    List<Finding> detect(SystemModel model);
}
