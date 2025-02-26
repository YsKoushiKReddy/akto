import React, { useEffect, useState, useReducer } from 'react'
import { LegacyCard, HorizontalGrid, TextField, VerticalStack, Text } from '@shopify/polaris'
import Dropdown from '../../../components/layouts/Dropdown'
import "./DataTypes.css"
import ConditionsPicker from '../../../components/ConditionsPicker'
import { useLocation, useNavigate } from 'react-router-dom'
import transform from './transform'
import func from "@/util/func"
import api from '../api'
import {produce} from "immer"
import DetailsPage from '../../../components/DetailsPage'
import InformationBannerComponent from '../../quick_start/components/shared/InformationBannerComponent'
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo'

const statusItems = [
  {
    id:"True",
    label: "True",
    value: "true",
  },
  {
    id:"False",
    label: "False",
    value: "false",
  }
]

const operatorOptions = [
  {
    id:"OR",
    label: "OR",
    value: "OR"
  },
  {
    id:"AND",
    label: "AND",
    value: "AND"
  }
]

const selectOptions = [
  {
    id:"EQUALS_TO",
    label: 'Equals to',
    value: 'EQUALS_TO'
  },
  {
    id:"REGEX",
    label: 'Matches regex',
    value: 'REGEX'
  },
  {
    id:"STARTS_WITH",
    label: 'Starts with',
    value: 'STARTS_WITH'
  },
  {
    id:"ENDS_WITH",
    label: 'Ends with',
    value: 'ENDS_WITH'
  },
  {
    id:"IS_NUMBER",
    label: 'Is number',
    value: 'IS_NUMBER',
    type: "NUMBER"
  }
]

const requestItems = [
  {
    id:"1",
    label: 'Request',
    value: '1'
  },
  {
    id:"2",
    label: 'Response',
    value: '2'
  },
  {
    id:"3",
    label: 'Both Request and Response',
    value: '3'
  },
  {
    id:"4",
    label: 'Neither Request nor Response',
    value: '4'
  }
]

function conditionStateReducer(draft, action) {
  try {
    switch (action.type) {
      case "update": {
        if (action.key) {
          Object.assign(draft[action.key], action.obj);
        } else {
          Object.assign(draft, action.obj);
        }
        break;
      }
      case "addPredicates": {
        if (action.key) {
          draft[action.key].predicates.push(action.condition);
        }
        break;
      }
      case "updatePredicates": {
        if (action.key) {
          Object.assign(draft[action.key].predicates[action.index], action.obj)
        }
        break;
      }
      case "deletePredicates": {
        if (action.key) {
          draft[action.key].predicates = draft[action.key].predicates.filter((i, index) => index !== action.index);
        }
        break;
      }
      default: break;
    }
  } catch {
    return draft;
  }
}

function DataTypes() {

  const location = useLocation();
  const navigate = useNavigate();
  const isNew = location?.state == undefined || Object.keys(location?.state).length == 0
  const pageTitle = (isNew || (location?.state?.regexObj)) ? "Add data type" : "Configure data type"
  const currObj = location?.state?.regexObj ? transform.getRegexObj(location?.state?.regexObj) : transform.initialObj
  const initialState = pageTitle === "Add data type" ? isNew ? transform.initialObj : currObj : transform.fillInitialState(location.state);

  const [currState, dispatchCurrState] = useReducer(produce((draft, action) => conditionStateReducer(draft, action)), currObj);
  const [change, setChange] = useState(false)
  const resetFunc =()=>{
    dispatchCurrState({type:"update", obj:initialState})
    setChange(false)
  }

  useEffect(() => {
      resetFunc()
  },[])

  useEffect(() => {
    if (func.deepComparison(currState, initialState)) {
      setChange(false);
    } else {
      setChange(true);
    }
  }, [currState])

  const saveAction = async () => {
    console.log(currState)
    if (currState.dataType === 'Akto') {

      const keyArr = currState.keyConditions.predicates.map(transform.convertMapFunction)
      const valueArr = currState.valueConditions.predicates.map(transform.convertMapFunction)

      let obj = {
        name: currState.name,
        redacted:currState.redacted,
        categoriesList: currState?.categoriesList || [],
        operator: currState.operator,
        keyConditionFromUsers: keyArr,
        keyOperator: currState.keyConditions.operator,
        valueConditionFromUsers: valueArr,
        valueOperator: currState.valueConditions.operator,
        dataTypePriority: currState?.priority ? currState.priority.toUpperCase() : "",
        ...transform.convertToSensitiveData(currState.sensitiveState),
        
      }
      api.saveAktoDataType(obj).then((response) => {
        func.setToast(true, false, "Data type updated successfully");
        setChange(false);
        navigate(null, {
          state: { name: response.aktoDataType.name, dataObj: response.aktoDataType, type: "Akto" },
          replace: true
        })
      })
    }
    else {
      let payloadObj = transform.convertDataForCustomPayload(currState)
      api.saveCustomDataType(payloadObj).then((response) => {
        if (pageTitle === "Add data type") {
          func.setToast(true, false, "Data type added successfully");
        } else {
          func.setToast(true, false, "Data type updated successfully");
        }
        setChange(false);
        navigate(null,
          {
            state: { name: response.customDataType.name, dataObj: response.customDataType, type: "Custom" },
            replace: true
          })
      })
    }
  }

  const handleChange = (obj) => {
    dispatchCurrState({type:"update", obj:obj})
  }

  const errorMessage = func.nameValidationFunc(currState.name)
  const descriptionCard = (
    <LegacyCard title="Details" key="desc">
      <LegacyCard.Section>
        <HorizontalGrid gap="4" columns={2}>
          <TextField id={"name-field"} label="Name" helpText="Name the data type"
            value={currState.name} placeholder='NEW_CUSTOM_DATA_TYPE'
            {...pageTitle === "Add data type" ? {onChange: (val) => handleChange({name: val})} : {}}
            requiredIndicator={true}
            {...errorMessage.length > 0 ? {error: errorMessage} : {}}
            />
          {currState.dataType === 'Custom' ?
            <Dropdown id={"active-dropdown"} menuItems={statusItems}
              selected={(val) => { handleChange({ active: val }) }}
              initial={currState.active} label="Active" />
            : null}
        </HorizontalGrid>
      </LegacyCard.Section>
    </LegacyCard>
  )

  const handleDispatch = (val, key) => {
    if (val?.key === "condition") {
      dispatchCurrState({ ...val, key: key, type: `${val.type}Predicates` })
    } else {
      dispatchCurrState({ ...val, key: key });
    }
  }

  const conditionsCard = (
    <LegacyCard title={
      <Text variant='headingMd'>
        Data type conditions
      </Text>
    }
      key="condition">
        <ConditionsPicker 
          id={"key"}
          title="Key conditions" 
          param = "param_name" 
          conditions={currState.keyConditions.predicates}
          selectOptions={selectOptions}
          operator={currState.keyConditions.operator}
          dispatch={(val) => { handleDispatch(val, "keyConditions") }}
          tooltipContent="Conditions for matching 'key' in payload type"
        />
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
          <Dropdown id={"condition"} menuItems={operatorOptions} initial={currState.operator} 
          selected={(val) => { handleChange({ operator: val }) }} />
          <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
        </div>
        <ConditionsPicker 
          id={"value"}
          title="Value conditions" 
          param = "param_value" 
          conditions={currState.valueConditions.predicates}
          selectOptions={selectOptions}
          operator={currState.valueConditions.operator}
          dispatch={(val) => { handleDispatch(val, "valueConditions") }}
          tooltipContent="Conditions for matching 'value' in payload type"
        />
    </LegacyCard>
  )

  const compareFunc = () => {
    return !change
  }

  const requestCard = (
    <VerticalStack gap="5" key="sensitive">
      <LegacyCard title={
          <TitleWithInfo 
            textProps={{variant: 'headingMd'}} 
            titleText={"Sensitive"} 
            tooltipContent={"Positions to detect data-type in"}
          />}
      >
        <LegacyCard.Section>
          <p>Mark the location where you want the data type to be sensitive</p>
          <br/>
          <HorizontalGrid columns="4">
            <Dropdown id={"sensitive-position"} menuItems = {requestItems} initial={currState.sensitiveState} 
            selected={(val) => { handleChange({ sensitiveState: val }) }}/>
          </HorizontalGrid >
        </LegacyCard.Section>
      </LegacyCard>
    </VerticalStack>
  )

  const redactCard = (
    <VerticalStack gap="5" key="redact">
      <LegacyCard title={
          <TitleWithInfo 
            textProps={{variant: 'headingMd'}} 
            titleText={"Redact"} 
            tooltipContent={"Redact the current subtype"}
            docsUrl={"https://docs.akto.io/api-inventory/how-to/redact-sensitive-data"}
          />}
      >
        <div className='card-items'>
          <InformationBannerComponent docsUrl={""} content="When enabled, existing sample payload values will be deleted, and this data type will be redacted in future payloads. Please note that your API Inventory, Sensitive data etc. will be intact. We will simply be deleting the sample payload values.">
          </InformationBannerComponent>
        </div>
        <LegacyCard.Section>
          <p>Redact this data type</p>
          <br/>
          <HorizontalGrid columns="4">
            <Dropdown id={"redact-position"} menuItems = {statusItems} initial={currState.redacted}
            selected={(val) => { handleChange({ redacted: val }) }}/>
          </HorizontalGrid >
        </LegacyCard.Section>
      </LegacyCard>
    </VerticalStack>
  )

  const TestTemplateCard = (
    <VerticalStack gap="5" key="testTemplate">
      <LegacyCard title={
        <TitleWithInfo
          textProps={{ variant: 'headingMd' }}
          titleText={"Test templates"}
          tooltipContent={"Create test template for this data type"}
        />}
      >
        <div className='card-items'>
          <InformationBannerComponent docsUrl={""} content="When enabled, test template is created and synced with this data type.">
          </InformationBannerComponent>
        </div>
        <LegacyCard.Section>
          <Checkbox label={"Create test template for this data type"} checked={!(currState.skipDataTypeTestTemplateMapping)} onChange={() => {
            handleChange({ skipDataTypeTestTemplateMapping: !currState.skipDataTypeTestTemplateMapping })
          }} />
        </LegacyCard.Section>
      </LegacyCard>
    </VerticalStack>
  )

  let components = (!isNew && currState.dataType === 'Akto') ? [descriptionCard, conditionsCard, requestCard, redactCard] : [descriptionCard, conditionsCard, requestCard, redactCard, TestTemplateCard]

  return (
    <DetailsPage
      pageTitle={
          <TitleWithInfo 
            tooltipContent={"Edit your custom data type"} 
            titleText={pageTitle} 
            docsUrl="https://docs.akto.io/api-inventory/how-to/create-a-custom-data-type"
          />
      }
      backUrl="/dashboard/observe/sensitive"
      saveAction={saveAction}
      discardAction={resetFunc}
      isDisabled={compareFunc}
      components={components}
    />
  )
}

export default DataTypes