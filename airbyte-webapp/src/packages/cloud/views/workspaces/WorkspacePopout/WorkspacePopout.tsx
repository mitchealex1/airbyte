import React from "react";
import styled from "styled-components";
import { components } from "react-select";
import { FormattedMessage } from "react-intl";
import { MenuListComponentProps } from "react-select/src/components/Menu";

import { Popout } from "components";
import { IDataItem } from "components/base/DropDown/components/Option";
import {
  useGetWorkspace,
  useListWorkspaces,
  useWorkspaceService,
} from "packages/cloud/services/workspaces/WorkspacesService";

import ExitIcon from "./components/ExitIcon";

const BottomElement = styled.div`
  background: ${(props) => props.theme.greyColro0};
  padding: 6px 16px 8px;
  width: 100%;
  min-height: 34px;
  border-top: 1px solid ${(props) => props.theme.greyColor20};
  color: ${(props) => props.theme.primaryColor};
`;

const Block = styled.div`
  cursor: pointer;
  color: ${({ theme }) => theme.primaryColor};
  font-size: 11px;
  line-height: 13px;
  vertical-align: center;

  &:hover {
    opacity: 0.7;
  }
`;

const TextBlock = styled.div`
  margin-left: 10px;
  vertical-align: text-top;
  display: inline-block;
`;

type MenuWithRequestButtonProps = MenuListComponentProps<IDataItem, false>;

const WorkspacesList: React.FC<MenuWithRequestButtonProps> = ({
  children,
  ...props
}) => {
  const { selectWorkspace } = useWorkspaceService();
  return (
    <>
      <components.MenuList {...props}>{children}</components.MenuList>
      <BottomElement>
        <Block onClick={() => selectWorkspace("")}>
          <ExitIcon />
          <TextBlock>
            <FormattedMessage id="workspaces.viewAllWorkspaces" />
          </TextBlock>
        </Block>
      </BottomElement>
    </>
  );
};

const WorkspacePopout: React.FC<{
  children: (props: { onOpen: () => void; value: any }) => React.ReactNode;
}> = ({ children }) => {
  const { data: workspaces } = useListWorkspaces();
  const { selectWorkspace, currentWorkspaceId } = useWorkspaceService();
  const { data: workspace } = useGetWorkspace(currentWorkspaceId || "");

  return (
    <Popout
      targetComponent={(targetProps) =>
        children({ onOpen: targetProps.onOpen, value: workspace?.name })
      }
      components={{
        MenuList: WorkspacesList,
      }}
      isSearchable={false}
      options={workspaces?.map((workspace) => ({
        value: workspace.workspaceId,
        label: workspace.name,
      }))}
      value={workspace?.workspaceId}
      onChange={({ value }) => selectWorkspace(value)}
    />
  );
};

export { WorkspacePopout };
