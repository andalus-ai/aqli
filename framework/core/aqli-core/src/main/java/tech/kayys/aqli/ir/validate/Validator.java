package tech.kayys.aqli.ir.validate;

import tech.kayys.alkhawarizm.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.alkhawarizm.core.tensor.*;

import tech.kayys.aqli.ir.*;
import java.util.*;

public interface Validator {
    void validate(List<GValue> inputs, Map<String, GAttrValue> attrs);
}